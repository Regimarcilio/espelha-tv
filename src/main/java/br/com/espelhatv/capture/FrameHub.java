package br.com.espelhatv.capture;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Distribui o frame mais recente para todos os espectadores. Cada cliente tem
 * fila de capacidade 1: se a TV atrasar, perde frames antigos em vez de criar
 * backlog e latência crescente.
 */
public final class FrameHub {
    private final CopyOnWriteArrayList<BlockingQueue<Frame>> clients = new CopyOnWriteArrayList<>();
    private final AtomicReference<Frame> latest = new AtomicReference<>();

    public BlockingQueue<Frame> subscribe() {
        BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(1);
        clients.add(queue);
        Frame current = latest.get();
        if (current != null) {
            queue.offer(current);
        }
        return queue;
    }

    public void unsubscribe(BlockingQueue<Frame> queue) {
        if (queue != null) {
            clients.remove(queue);
        }
    }

    public void publish(Frame frame) {
        if (frame == null) {
            return;
        }
        latest.set(frame);
        for (BlockingQueue<Frame> queue : clients) {
            queue.poll();
            queue.offer(frame);
        }
    }

    public Frame latest() {
        return latest.get();
    }

    public int viewerCount() {
        return clients.size();
    }
}
