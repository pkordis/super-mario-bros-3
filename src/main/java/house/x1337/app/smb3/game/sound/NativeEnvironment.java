package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.annotation.Singleton;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reads and writes the process's environment variables, for native libraries loaded later in the same process.
 *
 * <p>Java has no way to set an environment variable for its own process: {@link System#getenv()} is a copy taken
 * when the JVM started. A native library reads the real process environment, so {@link #set(String, String)}
 * calls the operating system directly, through the Foreign Function API: {@code SetEnvironmentVariableW} on
 * Windows, {@code setenv} elsewhere. The variable is seen by libraries that read it after the call, not by
 * {@link System#getenv(String)}, and not by child processes started through {@link ProcessBuilder}.
 */
@Singleton
public final class NativeEnvironment {
    /**
     * @param name a variable name
     * @return the variable's value as the JVM started with it, or {@code null}
     */
    public String get(final String name) {
        return System.getenv(name);
    }

    /**
     * Sets a variable in the process environment.
     *
     * @param name  the variable name
     * @param value its value
     * @throws IllegalStateException when the operating system refuses, or native calls are not allowed
     */
    public void set(final String name, final String value) {
        try (Arena arena = Arena.ofConfined()) {
            if (isWindows()) {
                final MethodHandle setEnvironmentVariable = Linker.nativeLinker().downcallHandle(
                    SymbolLookup.libraryLookup("kernel32", arena).find("SetEnvironmentVariableW").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
                );
                final int result = (int) setEnvironmentVariable.invokeExact(
                    arena.allocateFrom(name, UTF_16LE),
                    arena.allocateFrom(value, UTF_16LE)
                );
                check(result != 0, name);
            } else {
                final Linker linker = Linker.nativeLinker();
                final MethodHandle setenv = linker.downcallHandle(
                    linker.defaultLookup().find("setenv").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)
                );
                final int result = (int) setenv.invokeExact(
                    arena.allocateFrom(name, UTF_8),
                    arena.allocateFrom(value, UTF_8),
                    1
                );
                check(result == 0, name);
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("Could not set " + name, e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    private void check(final boolean succeeded, final String name) {
        if (!succeeded) {
            throw new IllegalStateException("The operating system refused to set " + name);
        }
    }
}
