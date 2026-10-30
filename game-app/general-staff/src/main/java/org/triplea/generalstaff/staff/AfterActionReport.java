package org.triplea.generalstaff.staff;

import games.strategy.engine.data.GameData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.triplea.generalstaff.observe.HistoryLog;
import org.triplea.generalstaff.observe.HistoryLogExporter;

/**
 * What the staff did in the hosted game so far, the operator's decisions, and the engine's own
 * history of the game, so the commander can check the execution against the plan.
 */
public record AfterActionReport(
    List<String> players,
    int round,
    String currentStep,
    boolean gameOver,
    List<ExecutionRecord> records,
    List<ApprovalGate.Decision> decisions,
    HistoryLog history) {

  public static AfterActionReport of(
      final Collection<StaffPlayer> staffs,
      final ApprovalGate gate,
      final GameData data,
      final boolean gameOver) {
    final List<ExecutionRecord> records = new ArrayList<>();
    staffs.forEach(staff -> records.addAll(staff.records()));
    records.sort(Comparator.comparing(ExecutionRecord::at));
    return new AfterActionReport(
        staffs.stream().map(StaffPlayer::getName).sorted().toList(),
        data.getSequence().getRound(),
        data.getSequence().getStep().getName(),
        gameOver,
        records,
        gate.history(),
        new HistoryLogExporter().export(data));
  }
}
