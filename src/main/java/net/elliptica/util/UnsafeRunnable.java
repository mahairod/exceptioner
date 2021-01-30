package net.elliptica.util;

public interface UnsafeRunnable<E extends Exception> {
	void run() throws E;
}
