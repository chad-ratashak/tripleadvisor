package org.triplea.generalstaff.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Where the engine thread waits for a human. The staff player calls {@link #await} inside its turn
 * and the engine blocks, exactly as it does for a human at the keyboard, until the operator
 * approves (optionally with edited orders), rejects, or a timeout passes. Automatic modes exist for
 * tests and scripted runs.
 */
public final class ApprovalGate {
  public enum Mode {
    MANUAL,
    AUTO_APPROVE,
    AUTO_REJECT
  }

  /** What the engine is waiting on. */
  public record Pending(String step, String player, List<PlannedOrder> proposed, Instant since) {}

  /** The operator's answer. */
  public record Decision(
      String step, boolean approved, List<PlannedOrder> orders, String decidedBy, Instant at) {}

  private final Mode mode;
  private final Duration timeout;
  private final Object lock = new Object();
  private final List<Decision> history = new ArrayList<>();
  @Nullable private Pending pending;
  @Nullable private Decision answer;
  private boolean released;

  private ApprovalGate(final Mode mode, final Duration timeout) {
    this.mode = mode;
    this.timeout = timeout;
  }

  /**
   * A gate a person answers through the API or CLI; unanswered proposals are rejected after the
   * timeout.
   */
  public static ApprovalGate manual(final Duration timeout) {
    return new ApprovalGate(Mode.MANUAL, timeout);
  }

  public static ApprovalGate autoApprove() {
    return new ApprovalGate(Mode.AUTO_APPROVE, Duration.ZERO);
  }

  public static ApprovalGate autoReject() {
    return new ApprovalGate(Mode.AUTO_REJECT, Duration.ZERO);
  }

  public Mode mode() {
    return mode;
  }

  /** Engine side: blocks until the operator decides on the proposed orders for this step. */
  public Decision await(final String step, final String player, final List<PlannedOrder> proposed) {
    if (mode == Mode.AUTO_APPROVE) {
      return record(new Decision(step, true, proposed, "auto-approve", Instant.now()));
    }
    if (mode == Mode.AUTO_REJECT) {
      return record(new Decision(step, false, List.of(), "auto-reject", Instant.now()));
    }
    synchronized (lock) {
      pending = new Pending(step, player, proposed, Instant.now());
      answer = null;
      lock.notifyAll();
      final long deadline = System.nanoTime() + timeout.toNanos();
      while (answer == null && !released) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          break;
        }
        try {
          lock.wait(Math.max(1, remaining / 1_000_000));
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      final Decision decision =
          answer != null
              ? answer
              : new Decision(
                  step, false, List.of(), released ? "released" : "timeout", Instant.now());
      pending = null;
      answer = null;
      return record(decision);
    }
  }

  /** Operator side: what, if anything, the engine is waiting on. */
  public Optional<Pending> pending() {
    synchronized (lock) {
      return Optional.ofNullable(pending);
    }
  }

  /** Operator side: blocks until the engine proposes something or the time is up. */
  public Optional<Pending> awaitPending(final Duration patience) {
    synchronized (lock) {
      final long deadline = System.nanoTime() + patience.toNanos();
      while (pending == null) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          break;
        }
        try {
          lock.wait(Math.max(1, remaining / 1_000_000));
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      return Optional.ofNullable(pending);
    }
  }

  /** Approves the proposed orders as they stand. */
  public boolean approve(final String decidedBy) {
    synchronized (lock) {
      return pending != null && decide(true, pending.proposed(), decidedBy);
    }
  }

  /** Approves with edited orders. */
  public boolean approve(final List<PlannedOrder> orders, final String decidedBy) {
    synchronized (lock) {
      return decide(true, orders, decidedBy);
    }
  }

  public boolean reject(final String decidedBy) {
    synchronized (lock) {
      return decide(false, List.of(), decidedBy);
    }
  }

  /** Lets a blocked engine thread go (as a rejection); used when the host shuts down. */
  public void release() {
    synchronized (lock) {
      released = true;
      lock.notifyAll();
    }
  }

  public List<Decision> history() {
    synchronized (lock) {
      return List.copyOf(history);
    }
  }

  private boolean decide(final boolean approved, final List<PlannedOrder> orders, final String by) {
    if (pending == null) {
      return false;
    }
    answer = new Decision(pending.step(), approved, List.copyOf(orders), by, Instant.now());
    lock.notifyAll();
    return true;
  }

  private Decision record(final Decision decision) {
    synchronized (lock) {
      history.add(decision);
    }
    return decision;
  }
}
