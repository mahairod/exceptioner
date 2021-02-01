package net.elliptica.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ErrorWrapper {
	private final Log logger;

	public ErrorWrapper(Log logger) {
		this.logger = logger;
	}

	public <E extends Exception> void errorToWarn(UnsafeRunnable<E> runnable, String message) {
		errorToWarn(message, runnable);
	}

	public interface
	RunCtx<CurExc extends Exception, ExtExc extends Exception> {
		RunCtx<CurExc, ExtExc>
		catching(Class<CurExc> erType, String msg, BiFunction<String, CurExc, ExtExc> conv) throws CurExc, ExtExc;

		RunCtx<CurExc, CurExc>
		catching(Class<CurExc> erType, String msg) throws CurExc;

		<E2 extends Exception>
		RunCtx<E2, E2>
		on(Class<E2> erType);

		RunCtx<CurExc, ExtExc>
		print(Supplier<String> msgProv);

		<E2 extends Exception>
		RunCtx<CurExc, E2>
		wrap(BiFunction<String, CurExc, E2> wrapper);

		RunCtx<CurExc, ExtExc>
		commit() throws CurExc, ExtExc;

		<E2 extends Exception>
		RunCtx<E2, E2> or(Class<E2> erType2) throws CurExc, ExtExc;
	}

	public static <E extends Exception, ExtExc extends Exception>
	RunCtx<E, ExtExc> run(UnsafeRunnable<E> runnable) {
		class RunCtxFree implements RunCtx<E, ExtExc> {
			@Override
			public RunCtx<E, ExtExc> catching(Class<E> t, String msg, BiFunction<String, E, ExtExc> conv) { return this; }
			@Override
			public RunCtx<E, E> catching(Class<E> t, String msg) {
				return (RunCtx<E, E>) this;
			}

			@Override
			public <E2 extends Exception> RunCtx<E2, E2> on(Class<E2> erType) {
				return (RunCtx<E2, E2>) this;
			}

			@Override
			public RunCtx<E, ExtExc> print(Supplier<String> msgProv) { return this; }

			@Override public
			<E2 extends Exception>
			RunCtx<E, E2> wrap(BiFunction<String, E, E2> wrapper) {
				return (RunCtx<E, E2>) this;
			}
			@Override
			public RunCtx<E, ExtExc> commit() {
				return this;
			}

			@Override
			public <E2 extends Exception> RunCtx<E2, E2> or(Class<E2> erType2) {
				return (RunCtx<E2, E2>) this;
			}
		}
		RunCtxFree ctxFree = new RunCtxFree();

		class Catcher<E extends Exception, ExtExc extends Exception> {
			private final Class<? extends E> excType;
			String msg;
			BiFunction<String, E, ExtExc> wrapper;

			public Catcher(Class<? extends E> exc) {
				this.excType = exc;
			}
		}

		class RunCtx3 implements RunCtx<E, ExtExc> {
			private final E exc;
			private final Log log;
			private Catcher<E, ExtExc> catcher;

			public RunCtx3(E exc, Log log) {
				this.exc = exc;
				this.log = log;
			}

			@Override
			public RunCtx<E, ExtExc> catching(Class<E> erType, String msg, BiFunction<String, E, ExtExc> conv) throws E, ExtExc {
				return on(erType).print(()->msg).wrap(conv).commit();
			}

			@Override
			public RunCtx<E, E> catching(Class<E> erType, String msg) throws E {
				return on(erType).print(()->msg).commit();
			}


			@Override
			public <E2 extends Exception> RunCtx<E2, E2> on(Class<E2> erType) {
				catcher = new Catcher(erType);
				return (RunCtx<E2, E2>) this;
			}

			@Override
			public RunCtx3 print(Supplier<String> msgProv) {
				catcher.msg = msgProv.get();
				return this;
			}

			@Override
			public <E2 extends Exception> RunCtx<E, E2> wrap(BiFunction<String, E, E2> wrapper) {
				((Catcher<E, E2>) catcher).wrapper = wrapper;
				return (RunCtx<E, E2>) this;
			}

			@Override
			public RunCtx<E, ExtExc> commit() throws ExtExc {
				if (!catcher.excType.isInstance(exc)) {
					return this;
				}
				log.warn(catcher.msg + ": " + toWarnMessage(exc));
				if (catcher.wrapper != null) {
					throw catcher.wrapper.apply(catcher.msg, exc);
				}
				return ctxFree;
			}

			@Override
			public <E2 extends Exception> RunCtx<E2, E2> or(Class<E2> erType2) throws ExtExc {
				return commit().on(erType2);
			}

		}

		try {
			runnable.run();
			return ctxFree;
		} catch (Exception ex) {
			Log log = LogFactory.getLog(Reflection.getCallerClass());
			return new RunCtx3((E)ex, log);
		}
	}

	public static <E extends Exception, E1 extends E, E2 extends E, ExtExc extends Exception>
	void err2Warn(UnsafeRunnable<E> runnable,
					 Class<E1> erType1, String msg1, BiFunction<String, E, ExtExc> conv1,
					 Class<E2> erType2, String msg2, BiFunction<String, E, ExtExc> conv2
	) throws E, ExtExc {
		final Log log = LogFactory.getLog(Reflection.getCallerClass(2));
		ErrorWrapper wrapper = new ErrorWrapper(log);
		wrapper.errorToWarn(runnable,
			erType1, msg1, conv1,
			erType2, msg2, conv2
		);
	}

	public <E extends Exception> void errorToWarn(String message, UnsafeRunnable<E> runnable) {
		try {
			runnable.run();
		} catch (Exception ex) {
			logger.warn(message + ": " + toWarnMessage(ex));
		}
	}

	public <E extends Exception, E1 extends E, E2 extends E>
	void errorToWarn(UnsafeRunnable<E> runnable,
	                 Class<E1> erType1, String msg1,
	                 Class<E2> erType2, String msg2
	) throws E {
		runNCatch(runnable, toArr(msg1, msg2), erType1, erType2);
	}

	public <E extends Exception, E1 extends E, E2 extends E, ExtExc extends Exception>
	void errorToWarn(UnsafeRunnable<E> runnable,
	                 Class<E1> erType1, String msg1, BiFunction<String, E, ExtExc> conv1,
	                 Class<E2> erType2, String msg2, BiFunction<String, E, ExtExc> conv2
	) throws E, ExtExc {
		runNCatch(runnable, toArr(msg1, msg2), toArr(conv1, conv2), erType1, erType2);
	}

	public <E extends Exception, E1 extends E, E2 extends E, E3 extends E>
	void errorToWarn(UnsafeRunnable<E> runnable,
	                 Class<E1> erType1, String msg1,
	                 Class<E2> erType2, String msg2,
	                 Class<E3> erType3, String msg3
	) throws E {
		runNCatch(runnable, toArr(msg1, msg2, msg3), erType1, erType2, erType3);
	}

	private <E extends Exception>
	void runNCatch(UnsafeRunnable<E> runnable, String messages[], Class<? extends E> ... errorTypes) throws E {
		this.<E,E>runNCatch(runnable, messages, getEmptyConvs(), errorTypes);
	}

	private <E extends Exception, ExtExc extends Exception>
	void runNCatch(UnsafeRunnable<E> runnable, String[] messages, BiFunction<String, E, ExtExc>[] convs, Class<? extends E> ... errorTypes) throws E, ExtExc {
		try {
			runnable.run();
		} catch (Exception ex) {
			logWarn((E)ex, messages, errorTypes, convs);
		}
	}

	private <E extends Exception, ExtExc extends Exception>
	void logWarn(E ex, String messages[], Class<? extends Exception> errorTypes[], BiFunction<String, E, ExtExc>[] convs) throws E, ExtExc {
		for (int i = 0; i < errorTypes.length; i++) {
			if (errorTypes[i].isInstance(ex)) {
				String msg = messages[i] + ": " + toWarnMessage(ex);
				logger.warn(msg);
				if (convs[i] != null) {
					throw convs[i].apply(msg, ex);
				}
				return;
			}
		}
		throw ex;
	}

	private static <E extends Exception, ExtExc extends Exception>
	BiFunction<String, E, ExtExc>[] getEmptyConvs() {
		return (BiFunction<String, E, ExtExc>[]) EMPTY_CONVS;
	}

	private static BiFunction<String, Exception, Exception>[] EMPTY_CONVS = new BiFunction[5];

	private static <T> T[] toArr(T... els) {
		return els;
	}

	public static String toWarnMessage(Exception ex) {
		return ex.getClass().getSimpleName() + " (" + ex.getMessage() + ")";
	}

}
