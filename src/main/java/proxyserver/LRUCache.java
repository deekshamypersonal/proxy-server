package proxyserver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class LRUCache {
    private final int capacity;
    private final Map<String, LRUCache.Node> cache;
    private final LRUCache.Node head;
    private final LRUCache.Node tail;
    private int currentSize = 0;

    private static final int MAX_SIZE = 200 * (1 << 20); // Cache size limit
    private static final int MAX_ELEMENT_SIZE = 10 * (1 << 20); // Max cache element size

    // ReentrantReadWriteLock for managing concurrent access
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public LRUCache(int capacity) {
        this.capacity = capacity;
        cache = new HashMap<>();
        head = new LRUCache.Node(null, null);
        tail = new LRUCache.Node(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public byte[] get(String key) {
        readLock.lock();  // Acquire read lock
        try {
            if (!cache.containsKey(key)) return null;
            LRUCache.Node node = cache.get(key);
            moveToHead(node);  // Move node to head for LRU
            return node.value;
        } finally {
            readLock.unlock();  // Release read lock
        }
    }

    public void put(String key, byte[] value) {
        writeLock.lock();  // Acquire write lock
        try {
            int elementSize = value.length;
            if (elementSize > MAX_ELEMENT_SIZE) return;

            if (cache.containsKey(key)) {
                LRUCache.Node node = cache.get(key);
                node.value = value;
                moveToHead(node);
            } else {
                LRUCache.Node newNode = new LRUCache.Node(key, value);
                cache.put(key, newNode);
                addNode(newNode);
                currentSize += elementSize;

                while (currentSize > MAX_SIZE) {
                    LRUCache.Node tail = popTail();
                    cache.remove(tail.key);
                    currentSize -= tail.value.length;
                }
            }
        } finally {
            writeLock.unlock();  // Release write lock
        }
    }

    private void addNode(LRUCache.Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void moveToHead(LRUCache.Node node) {
        removeNode(node);
        addNode(node);
    }

    private void removeNode(LRUCache.Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private LRUCache.Node popTail() {
        LRUCache.Node res = tail.prev;
        removeNode(res);
        return res;
    }

    static class Node {
        String key;
        byte[] value;
        LRUCache.Node prev;
        LRUCache.Node next;

        public Node(String key, byte[] value) {
            this.key = key;
            this.value = value;
        }
    }
}
