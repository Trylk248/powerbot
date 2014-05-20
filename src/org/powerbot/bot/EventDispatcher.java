package org.powerbot.bot;

import java.io.Closeable;
import java.util.AbstractCollection;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.powerbot.bot.rt4.activation.PaintEvent;

public abstract class EventDispatcher extends AbstractCollection<EventListener> implements Runnable, Closeable {
	protected final AtomicReference<Thread> thread;
	protected final CopyOnWriteArrayList<EventListener> listeners;
	protected final Map<EventListener, Long> bitmasks;
	protected final BlockingQueue<EventObject> queue;
	protected final Map<Class<? extends EventListener>, Integer> masks;

	public EventDispatcher() {
		thread = new AtomicReference<Thread>(null);
		listeners = new CopyOnWriteArrayList<EventListener>();
		bitmasks = new ConcurrentHashMap<EventListener, Long>();
		queue = new LinkedBlockingQueue<EventObject>();
		masks = new HashMap<Class<? extends EventListener>, Integer>();
	}

	private long getMask(final EventListener e) {
		long m = 0;

		for (final Map.Entry<Class<? extends EventListener>, Integer> entry : masks.entrySet()) {
			if (entry.getKey().isInstance(e)) {
				m |= entry.getValue();
			}
		}

		return m;
	}

	public final void dispatch(final EventObject e) {
		queue.offer(e);
	}

	public final void consume(final EventObject e) {
		consume(e, getType(e));
	}

	protected abstract void consume(final EventObject e, final int t);

	protected abstract int getType(final EventObject e);

	@Override
	public final void run() {
		if (!thread.compareAndSet(null, Thread.currentThread())) {
			return;
		}

		while (!Thread.interrupted()) {
			final EventObject o;

			try {
				o = queue.take();
			} catch (final InterruptedException ignored) {
				break;
			}

			try {
				consume(o);
			} catch (final Throwable ignored) {
			}
		}

		thread.set(null);
	}

	@Override
	public final void close() {
		final Thread t = thread.get();
		if (t != null) {
			t.interrupt();
		}
	}

	@Override
	public final Iterator<EventListener> iterator() {
		final Iterator<EventListener> e = listeners.iterator();
		return new Iterator<EventListener>() {
			private volatile EventListener o = null;

			@Override
			public boolean hasNext() {
				return e.hasNext();
			}

			@Override
			public EventListener next() {
				o = e.next();
				return o;
			}

			@Override
			public void remove() {
				if (o == null) {
					throw new IllegalStateException();
				}
				EventDispatcher.this.remove(o);
				o = null;
			}
		};
	}

	@Override
	public final int size() {
		return listeners.size();
	}

	@Override
	public final boolean add(final EventListener e) {
		if (listeners.addIfAbsent(e)) {
			bitmasks.put(e, getMask(e));
			return true;
		}

		return false;
	}

	public final boolean remove(final EventListener e) {
		if (listeners.remove(e)) {
			bitmasks.remove(e);
			return true;
		}

		return false;
	}

	public final boolean contains(final Class<? extends EventListener> o) {
		for (final EventListener e : listeners) {
			if (e.getClass().isAssignableFrom(o)) {
				return true;
			}
		}
		return false;
	}
}
