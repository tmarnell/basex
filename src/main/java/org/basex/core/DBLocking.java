package org.basex.core;

import static org.basex.core.Prop.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Manage read and write locks on arbitrary objects. Maximum of {@link MainProp#PARALLEL}
 * concurrent transactions are allowed, further will be queued.
 *
 * This class prevents locking deadlocks by sorting all Objects to put locks on what
 * requires them to have be {@link Comparable}.
 *
 * Locks can only be released by the same thread which acquired it.
 *
 * This locking can be activated by setting {@link MainProp#DBLOCKING} to {@code true}.
 * It will get the default implementation in future versions.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Jens Erat
 */
public final class DBLocking implements ILocking {
  /** Stores one lock for each object ever used for locking. */
  private final Map<String, ReentrantReadWriteLock> locks =
      new HashMap<String, ReentrantReadWriteLock>();
  /**
   * Currently running transactions.
   * Used as monitor for atomizing access to {@link #queue}.
   */
  private int transactions;
  /**
   * Queue for transactions waiting.
   *
   * Used as monitor for waiting threads in queue.
   */
  private final Queue<Long> queue = new LinkedList<Long>();
  /** Stores a list of objects each transaction has locked. */
  private final ConcurrentMap<Long, String[]> locked
      = new ConcurrentHashMap<Long, String[]>();
  /** BaseX database context. */
  private final MainProp mprop;

  /**
   * Initialize new Locking instance.
   * @param mp Main properties, used to read parallel transactions limit.
   */
  public DBLocking(final MainProp mp) {
    mprop = mp;
  }

  @Override
  public void acquire(final Progress pr, final StringList db) {
    // No databases specified: lock globally
    if(db == null) Util.notimplemented("Global locks in DBLocking not implemented yet.");

    final long thread = Thread.currentThread().getId();
    if(locked.containsKey(thread))
      throw new IllegalMonitorStateException("Thread already holds one or more locks.");

    // Wait in queue if necessary
    synchronized(queue) { // Guard queue and transaction, monitor for waiting in queue
      queue.add(thread);
      while(transactions >= Math.max(mprop.num(MainProp.PARALLEL), 1)
          || queue.peek() != thread) {
        try {
          queue.wait();
        } catch(final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
        final int t = transactions++;
        assert t <= Math.max(mprop.num(MainProp.PARALLEL), 1);
        queue.remove(thread);
    }

    // Sort entries and remove duplicates to prevent deadlocks
    final String[] objects = db.sort(true, true).unique().toArray();

    // Store for unlocking later
    locked.put(thread, objects);

    // Finally lock objects
    for(final String object : objects) {
      ReentrantReadWriteLock lock;
      synchronized(locks) { // Make sure each object lock is a singleton
        lock = locks.get(object);
        if(null == lock) {
          lock = new ReentrantReadWriteLock();
          locks.put(object, lock);
        }
      }
      (pr.updating ? lock.writeLock() : lock.readLock()).lock();
    }
  }

  @Override
  public void release(final Progress pr) {
    final String[] objects = locked.remove(Thread.currentThread().getId());
    if(null == objects)
      throw new IllegalMonitorStateException("No locks held by current thread");

    // Unlock all locks, no matter if read or write lock
    for(final String object : objects) {
      final ReentrantReadWriteLock lock = locks.get(object);
      if(lock.isWriteLockedByCurrentThread()) {
        assert 1 == lock.getWriteHoldCount() : "Unexpected write lock count: "
            + lock.getWriteHoldCount();
        lock.writeLock().unlock();
      } else {
        assert 1 == lock.getReadHoldCount() : "Unexpected read lock count: "
            + lock.getReadHoldCount();
        lock.readLock().unlock();
      }
    }

    // Allow another transaction to run
    synchronized(queue) {
      transactions--;
      queue.notifyAll();
    }
  }

  /**
   * Present current locking status. Not to be seen as a programming API but only for
   * debugging purposes.
   */
  @Override
  public String toString() {
    final String ind = "| ";
    final StringBuilder sb = new StringBuilder(NL);
    sb.append("Locking" + NL);
    sb.append(ind + "Transactions running: " + transactions + NL);
    sb.append(ind + "Transaction queue: " + queue + NL);
    sb.append(ind + "Held locks by object:" + NL);
    for(final Object object : locks.keySet())
      sb.append(ind + ind + object + " -> " + locks.get(object) + NL);
    sb.append(ind + "Held locks by transaction:" + NL);
    for(final Long thread : locked.keySet())
      sb.append(ind + ind + thread + " -> " + locked.get(thread) + NL);
    return sb.toString();
  }

}
