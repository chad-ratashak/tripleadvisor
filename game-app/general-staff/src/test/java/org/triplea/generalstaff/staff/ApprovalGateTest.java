package org.triplea.generalstaff.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ApprovalGateTest {
  private static final PlannedOrder ORDER =
      new PlannedOrder(List.of("Holland Belgium", "France"), "infantry", 2);

  @Test
  void automaticModesAnswerWithoutWaiting() {
    assertThat(ApprovalGate.autoApprove().await("s", "Germans", List.of(ORDER)).approved())
        .isTrue();
    assertThat(ApprovalGate.autoApprove().await("s", "Germans", List.of(ORDER)).orders())
        .containsExactly(ORDER);
    assertThat(ApprovalGate.autoReject().await("s", "Germans", List.of(ORDER)).approved())
        .isFalse();
  }

  @Test
  void manualGateBlocksTheEngineUntilTheOperatorDecides() throws Exception {
    final ApprovalGate gate = ApprovalGate.manual(Duration.ofSeconds(30));
    final CompletableFuture<ApprovalGate.Decision> engine =
        CompletableFuture.supplyAsync(
            () -> gate.await("germansCombatMove", "Germans", List.of(ORDER)));

    final ApprovalGate.Pending pending = gate.awaitPending(Duration.ofSeconds(10)).orElseThrow();
    assertThat(pending.step()).isEqualTo("germansCombatMove");
    assertThat(pending.proposed()).containsExactly(ORDER);
    assertThat(engine.isDone()).isFalse();

    final PlannedOrder edited =
        new PlannedOrder(List.of("Holland Belgium", "France"), "infantry", 1);
    assertThat(gate.approve(List.of(edited), "test")).isTrue();
    final ApprovalGate.Decision decision = engine.get(10, TimeUnit.SECONDS);

    assertThat(decision.approved()).isTrue();
    assertThat(decision.orders()).containsExactly(edited);
    assertThat(decision.decidedBy()).isEqualTo("test");
    assertThat(gate.pending()).isEmpty();
    assertThat(gate.history()).hasSize(1);
    assertThat(gate.approve("nobody")).isFalse();
  }

  @Test
  void timeoutAndReleaseCountAsRejections() throws Exception {
    final ApprovalGate quick = ApprovalGate.manual(Duration.ofMillis(200));
    assertThat(quick.await("s", "Germans", List.of()).decidedBy()).isEqualTo("timeout");

    final ApprovalGate released = ApprovalGate.manual(Duration.ofSeconds(30));
    final CompletableFuture<ApprovalGate.Decision> engine =
        CompletableFuture.supplyAsync(() -> released.await("s", "Germans", List.of(ORDER)));
    released.awaitPending(Duration.ofSeconds(10)).orElseThrow();
    released.release();
    assertThat(engine.get(10, TimeUnit.SECONDS).decidedBy()).isEqualTo("released");
  }

  @Test
  void aDecisionNamingAnotherStepIsRefused() throws Exception {
    final ApprovalGate gate = ApprovalGate.manual(Duration.ofSeconds(30));
    final CompletableFuture<ApprovalGate.Decision> engine =
        CompletableFuture.supplyAsync(
            () -> gate.await("germansCombatMove", "Germans", List.of(ORDER)));
    gate.awaitPending(Duration.ofSeconds(10)).orElseThrow();

    assertThat(gate.approve("germansNonCombatMove", "test")).isFalse();
    assertThat(gate.approve("germansNonCombatMove", List.of(ORDER), "test")).isFalse();
    assertThat(gate.reject("germansNonCombatMove", "test")).isFalse();
    assertThat(engine.isDone()).isFalse();

    assertThat(gate.approve("germansCombatMove", "test")).isTrue();
    assertThat(engine.get(10, TimeUnit.SECONDS).approved()).isTrue();
    assertThat(gate.reject("germansCombatMove", "test")).isFalse();
  }

  @Test
  void aDecisionForAnOlderProposalOfTheSameStepIsRefused() throws Exception {
    final ApprovalGate gate = ApprovalGate.manual(Duration.ofSeconds(30));
    final CompletableFuture<ApprovalGate.Decision> engine =
        CompletableFuture.supplyAsync(
            () -> gate.await("germansCombatMove", "Germans", List.of(ORDER)));
    final ApprovalGate.Pending pending = gate.awaitPending(Duration.ofSeconds(10)).orElseThrow();
    final Instant earlier = pending.since().minusSeconds(1);

    assertThat(gate.approve(new ApprovalGate.Shown("germansCombatMove", earlier), "test"))
        .isFalse();
    assertThat(gate.reject(new ApprovalGate.Shown("germansCombatMove", earlier), "test")).isFalse();
    assertThat(engine.isDone()).isFalse();

    assertThat(gate.approve(new ApprovalGate.Shown("germansCombatMove", pending.since()), "test"))
        .isTrue();
    assertThat(engine.get(10, TimeUnit.SECONDS).approved()).isTrue();
  }
}
