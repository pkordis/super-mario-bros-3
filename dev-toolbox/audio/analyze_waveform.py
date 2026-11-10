"""Analyse a 16-bit PCM WAV: leading silence, trailing silence, DC offset and how the sound ends.

Optionally compares the WAV's length with the true length of the Ogg Vorbis file it was decoded from
(the granule position of the last Ogg page), to spot samples a decoder added or dropped.

Usage:
    python audio/analyze_waveform.py --wav PATH [--ogg PATH] [--envelope-ms N]
"""
import argparse
import struct
import wave

import numpy as np

THRESHOLDS_DB = (-70, -60, -50, -40, -30)


def read_wav(path):
    with wave.open(path, 'rb') as w:
        if w.getsampwidth() != 2:
            raise SystemExit('Only 16-bit PCM WAV is supported')
        channels = w.getnchannels()
        rate = w.getframerate()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype='<i2').astype(np.float64)
    return data.reshape(-1, channels) / 32768.0, rate


def ogg_total_frames(path):
    """The granule position of the last Ogg page: the number of PCM frames the file really holds."""
    with open(path, 'rb') as f:
        data = f.read()
    last = -1
    position = data.find(b'OggS')
    while position >= 0:
        granule = struct.unpack_from('<q', data, position + 6)[0]
        if granule >= 0:
            last = granule
        position = data.find(b'OggS', position + 4)
    return last


def db(value):
    return 20 * np.log10(max(value, 1e-12))


def ms(frames, rate):
    return frames * 1000.0 / rate


def print_segments(args):
    """One line per sounding segment: start, end, length, peak and attack (time from start to the peak)."""
    samples, rate = read_wav(args.wav)
    peak = np.abs(samples).max(axis=1)
    loud = np.nonzero(peak > 10 ** (args.silence_db / 20))[0]
    name = args.wav.replace('\\', '/').split('/')[-1]
    if loud.size == 0:
        print(f'{name}: silent')
        return
    gap = int(args.gap_ms * rate / 1000)
    breaks = np.nonzero(np.diff(loud) > gap)[0]
    starts = np.concatenate(([loud[0]], loud[breaks + 1]))
    ends = np.concatenate((loud[breaks], [loud[-1]]))
    parts = []
    for start, end in zip(starts, ends):
        segment = peak[start:end + 1]
        parts.append(f'[{ms(start, rate):6.1f}-{ms(end, rate):6.1f} ms, {ms(end - start, rate):5.1f} ms long, '
                     f'peak {db(segment.max()):5.1f} dB at +{ms(segment.argmax(), rate):5.1f} ms]')
    print(f'{name}: total {ms(len(peak), rate):6.1f} ms; ' + ' '.join(parts))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--wav', required=True, help='16-bit PCM WAV to analyse')
    parser.add_argument('--ogg', help='The Ogg Vorbis source, to compare lengths')
    parser.add_argument('--envelope-ms', type=float, default=2.0, help='Window of the printed envelopes')
    parser.add_argument('--range', type=float, nargs=2, metavar=('START_MS', 'END_MS'),
                        help='Also print the envelope and the raw left-channel samples between two times')
    parser.add_argument('--segments', action='store_true',
                        help='Only list the sounding segments separated by at least --gap-ms of silence')
    parser.add_argument('--gap-ms', type=float, default=20.0, help='Silence that separates two segments')
    parser.add_argument('--silence-db', type=float, default=-70.0, help='Level below which a sample is silent')
    args = parser.parse_args()

    if args.segments:
        print_segments(args)
        return

    samples, rate = read_wav(args.wav)
    frames, channels = samples.shape
    peak = np.abs(samples).max(axis=1)
    print(f'{args.wav}: {channels} ch, {rate} Hz, {frames} frames, {ms(frames, rate):.1f} ms')
    if args.ogg:
        true_frames = ogg_total_frames(args.ogg)
        print(f'  Ogg says {true_frames} frames; decoded {frames} ({frames - true_frames:+d})')

    print(f'  Peak {db(peak.max()):.1f} dBFS')
    for threshold in THRESHOLDS_DB:
        above = np.nonzero(peak > 10 ** (threshold / 20))[0]
        if above.size == 0:
            print(f'  {threshold} dBFS: never reached')
            continue
        print(f'  {threshold} dBFS: first at {ms(above[0], rate):7.2f} ms, last at {ms(above[-1], rate):7.2f} ms')

    print('  DC offset per channel (mean): whole file ' + ', '.join(f'{m:+.5f}' for m in samples.mean(axis=0))
          + '; last 100 ms ' + ', '.join(f'{m:+.5f}' for m in samples[-rate // 10:].mean(axis=0)))
    print('  Last 4 frames: ' + '; '.join(', '.join(f'{v:+.5f}' for v in row) for row in samples[-4:]))
    print('  First 4 frames: ' + '; '.join(', '.join(f'{v:+.5f}' for v in row) for row in samples[:4]))

    # Where the audible part stops: the largest single-sample jump near the last -40 dBFS sample
    audible = np.nonzero(peak > 10 ** (-40 / 20))[0]
    if audible.size:
        end = audible[-1]
        window = samples[max(0, end - rate // 50):min(frames, end + rate // 200)]
        jumps = np.abs(np.diff(window, axis=0)).max(axis=1)
        print(f'  Around the audible end ({ms(end, rate):.2f} ms): largest sample-to-sample jump '
              f'{jumps.max():.4f} ({db(jumps.max()):.1f} dBFS), at {ms(max(0, end - rate // 50) + jumps.argmax(), rate):.2f} ms')
        tail = samples[end + 1:]
        if tail.size:
            print(f'  After it: peak {db(np.abs(tail).max()):.1f} dBFS over {ms(len(tail), rate):.1f} ms')

    step = max(1, int(rate * args.envelope_ms / 1000))

    def envelope(start, count):
        rows = []
        for index in range(count):
            begin = start + index * step
            chunk = peak[begin:begin + step]
            if chunk.size == 0:
                break
            rows.append(f'{ms(begin, rate):7.1f} ms {db(chunk.max()):6.1f} dB')
        return rows

    print(f'  Envelope of the start ({args.envelope_ms} ms windows, peak):')
    for row in envelope(0, 20):
        print('    ' + row)
    if audible.size:
        print('  Envelope around the audible end:')
        for row in envelope(max(0, audible[-1] - 10 * step), 16):
            print('    ' + row)
    if args.range:
        begin = int(args.range[0] * rate / 1000)
        end = int(args.range[1] * rate / 1000)
        print(f'  Envelope {args.range[0]:.1f}-{args.range[1]:.1f} ms:')
        for row in envelope(begin, max(1, (end - begin) // step)):
            print('    ' + row)
        print('  Left channel, every sample (ms: value):')
        values = samples[begin:end, 0]
        for index in range(0, len(values), 8):
            row = values[index:index + 8]
            print(f'    {ms(begin + index, rate):8.2f}: ' + ' '.join(f'{v:+.4f}' for v in row))


if __name__ == '__main__':
    main()
