"""Shorten an Ogg Vorbis file at a given time without re-encoding it.

The Vorbis packets past the cut are dropped and the last page is marked as the end of the stream, with its
granule position (the stream's total sample count) set to the cut. Nothing is decoded or encoded again, so the
audio that stays is bit for bit what it was.

How many samples each packet adds is worked out from the block sizes (identification header) and each packet's
mode bit. That assumes the two modes libvorbis always writes: mode 0 short blocks, mode 1 long blocks. The tool
checks the assumption against every page's own granule position and refuses to write if they disagree.

A decoder that honours the last granule (jME's, for a non-streamed load) stops exactly at the cut. One that plays
every packet (jME's streaming) stops at the end of the last packet kept, at most one block later.

Usage:
    python audio/trim_ogg_end.py --ogg IN.ogg --out OUT.ogg --end-ms 3240
    python audio/trim_ogg_end.py --ogg IN.ogg --report
"""
import argparse
import struct


def crc_table():
    """The Ogg page checksum: CRC-32 with polynomial 0x04C11DB7, no reflection, initial value 0."""
    table = []
    for index in range(256):
        value = index << 24
        for _ in range(8):
            value = ((value << 1) ^ 0x04C11DB7) if value & 0x80000000 else (value << 1)
        table.append(value & 0xFFFFFFFF)
    return table


CRC_TABLE = crc_table()


def ogg_crc(data):
    crc = 0
    for byte in data:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ CRC_TABLE[((crc >> 24) & 0xFF) ^ byte]
    return crc


def read_pages(data):
    pages = []
    pos = 0
    while pos < len(data):
        if data[pos:pos + 4] != b'OggS':
            raise ValueError(f'No Ogg page at byte {pos}')
        segments = data[pos + 26]
        lacing = list(data[pos + 27:pos + 27 + segments])
        body_start = pos + 27 + segments
        pages.append({
            'offset': pos,
            'header_type': data[pos + 5],
            'granule': struct.unpack_from('<q', data, pos + 6)[0],
            'lacing': lacing,
            'body_start': body_start,
            'end': body_start + sum(lacing),
        })
        pos = body_start + sum(lacing)
    return pages


def packets_of(data, pages):
    """Every packet as (bytes, index of the page it ends on, index of its last lacing value on that page)."""
    packets = []
    current = bytearray()
    for page_index, page in enumerate(pages):
        cursor = page['body_start']
        for lacing_index, size in enumerate(page['lacing']):
            current += data[cursor:cursor + size]
            cursor += size
            if size < 255:
                packets.append((bytes(current), page_index, lacing_index))
                current = bytearray()
    return packets


def samples_per_packet(packets):
    """Samples each audio packet completes, after the three header packets."""
    identification = packets[0][0]
    if identification[1:7] != b'vorbis' or identification[0] != 1:
        raise ValueError('Not a Vorbis stream')
    sizes = identification[28]
    short_block, long_block = 1 << (sizes & 0x0F), 1 << (sizes >> 4)
    counts = []
    previous = None
    for body, _, _ in packets[3:]:
        if not body or body[0] & 1:
            raise ValueError('Unexpected non-audio packet')
        block = long_block if (body[0] >> 1) & 1 else short_block
        counts.append(0 if previous is None else previous // 4 + block // 4)
        previous = block
    return counts


def check_granules(pages, packets, counts):
    """Each page's granule must equal the samples of the packets ending on it (the last page may be shorter)."""
    total = 0
    ends = {}
    for (_, page_index, _), count in zip(packets[3:], counts):
        total += count
        ends[page_index] = total
    for page_index, total in ends.items():
        page = pages[page_index]
        is_last = page_index == len(pages) - 1
        if page['granule'] != total and not (is_last and page['granule'] < total):
            raise ValueError(
                f'Page {page_index} says {page["granule"]} samples but its packets make {total}: '
                'the stream does not use the two standard modes'
            )


def trim(data, pages, packets, counts, end_sample):
    total = 0
    for packet_index, count in enumerate(counts):
        total += count
        if total >= end_sample:
            break
    else:
        raise ValueError(f'The file has only {total} samples')
    _, page_index, lacing_index = packets[3 + packet_index]
    page = pages[page_index]
    lacing = page['lacing'][:lacing_index + 1]
    body = data[page['body_start']:page['body_start'] + sum(lacing)]
    header = bytearray(data[page['offset']:page['offset'] + 27])
    header[5] = (header[5] | 0x04)
    struct.pack_into('<q', header, 6, end_sample)
    header[26] = len(lacing)
    struct.pack_into('<I', header, 22, 0)
    new_page = bytes(header) + bytes(lacing) + body
    new_page = new_page[:22] + struct.pack('<I', ogg_crc(new_page)) + new_page[26:]
    return bytes(data[:page['offset']]) + new_page, total


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--ogg', required=True, help='Ogg Vorbis file to read')
    parser.add_argument('--out', help='Where to write the shortened file')
    parser.add_argument('--end-ms', type=float, help='Where the shortened file ends, in milliseconds')
    parser.add_argument('--report', action='store_true', help='Only list the pages and the packet check')
    args = parser.parse_args()

    data = open(args.ogg, 'rb').read()
    pages = read_pages(data)
    packets = packets_of(data, pages)
    counts = samples_per_packet(packets)
    check_granules(pages, packets, counts)
    rate = struct.unpack_from('<I', packets[0][0], 12)[0]
    print(f'{args.ogg}: {len(pages)} pages, {len(packets) - 3} audio packets, {rate} Hz, '
          f'{pages[-1]["granule"]} samples ({pages[-1]["granule"] / rate * 1000:.1f} ms); granules check out')
    if args.report or args.out is None:
        for index, page in enumerate(pages):
            print(f'  page {index:3d} at byte {page["offset"]:7d}: granule {page["granule"]:9d} '
                  f'({page["granule"] / rate * 1000:9.1f} ms)')
        return
    end_sample = round(args.end_ms * rate / 1000)
    trimmed, packets_end = trim(data, pages, packets, counts, end_sample)
    open(args.out, 'wb').write(trimmed)
    print(f'{args.out}: ends at sample {end_sample} ({end_sample / rate * 1000:.1f} ms); the packets kept make '
          f'{packets_end} ({packets_end / rate * 1000:.1f} ms); {len(trimmed)} bytes, was {len(data)}')


if __name__ == '__main__':
    main()
