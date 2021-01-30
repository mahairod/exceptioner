package net.elliptica.util;

public interface UnsafeRunnable2<E1 extends Exception, E2 extends Exception> {
	void run() throws E1,E2;
}
