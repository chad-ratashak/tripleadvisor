package org.triplea.generalstaff.staff;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.player.Player;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.triplea.generalstaff.engine.HeadlessGameHost;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.plan.PlanReport;
import org.triplea.generalstaff.sim.SimulationPlayer;

/**
 * One hosted game with the General Staff playing one or more powers and stand-ins or the engine's
 * own AIs playing the others. This is what the API and CLI drive: set orders, run, answer the gate,
 * read the after-action report.
 */
public final class ExecutionSession implements AutoCloseable {
  /** Who plays the powers the staff does not. */
  public enum Others {
    /** Stand-ins that move nothing and buy nothing; battles still resolve. */
    IDLE,
    /** The engine's Fast AI. */
    FAST_AI
  }

  /** Where the hosted game stands. */
  public record Status(
      int round,
      String step,
      String stepPlayer,
      boolean running,
      boolean gameOver,
      @Nullable ApprovalGate.Pending pending,
      int recordCount) {}

  private final HeadlessGameHost host;
  private final Map<String, StaffPlayer> staffs;
  private final ApprovalGate gate;

  private ExecutionSession(
      final HeadlessGameHost host, final Map<String, StaffPlayer> staffs, final ApprovalGate gate) {
    this.host = host;
    this.staffs = staffs;
    this.gate = gate;
  }

  /** Hosts {@code data}; the staff plays every power in {@code staffPlayers} through one gate. */
  public static ExecutionSession start(
      final GameData data,
      final Set<String> staffPlayers,
      final Others others,
      final ApprovalGate gate,
      final Path workFolder)
      throws IOException {
    final Map<String, StaffPlayer> staffs = new TreeMap<>();
    for (final String name : staffPlayers) {
      if (data.getPlayerList().getPlayerId(name) == null) {
        throw new IllegalArgumentException("Unknown player: " + name);
      }
      staffs.put(name, new StaffPlayer(name, gate));
    }
    final Map<String, PlayerTypes.Type> types = new HashMap<>();
    for (final GamePlayer player : data.getPlayerList().getPlayers()) {
      final StaffPlayer staff = staffs.get(player.getName());
      types.put(player.getName(), staff != null ? staff.playerType() : otherType(others));
    }
    final HeadlessGameHost host = HeadlessGameHost.start(data, types, workFolder);
    staffs.values().forEach(staff -> staff.attach(host.game(), workFolder.resolve("saves")));
    return new ExecutionSession(host, staffs, gate);
  }

  /** Hosts {@code data} with the staff playing one power. */
  public static ExecutionSession start(
      final GameData data,
      final String staffPlayer,
      final Others others,
      final ApprovalGate gate,
      final Path workFolder)
      throws IOException {
    return start(data, Set.of(staffPlayer), others, gate, workFolder);
  }

  private static PlayerTypes.Type otherType(final Others others) {
    if (others == Others.FAST_AI) {
      return PlayerTypes.FAST_AI;
    }
    return new PlayerTypes.Type(SimulationPlayer.LABEL) {
      @Override
      public Player newPlayerWithName(final String name) {
        return new SimulationPlayer(name);
      }
    };
  }

  /** The orders a ranked plan asks for, in the staff's own order format. */
  public static List<PlannedStep> ordersFrom(final PlanReport.OperationalPlan plan) {
    final List<PlannedStep> steps = new ArrayList<>();
    for (final PlanReport.StepOrders step : plan.orders()) {
      final List<PlannedOrder> orders = new ArrayList<>();
      for (final PlanReport.OrderSummary order : step.orders()) {
        for (final UnitCount stack : order.units()) {
          orders.add(new PlannedOrder(order.route(), stack.type(), stack.count()));
        }
      }
      steps.add(new PlannedStep(step.step(), orders, plan.retreatAfterRound(), false));
    }
    return steps;
  }

  public HeadlessGameHost host() {
    return host;
  }

  public StaffPlayer staff(final String player) {
    final StaffPlayer staff = staffs.get(player);
    if (staff == null) {
      throw new IllegalArgumentException("The staff does not play " + player);
    }
    return staff;
  }

  /** The staff player when exactly one power is staffed. */
  public StaffPlayer staff() {
    if (staffs.size() != 1) {
      throw new IllegalStateException("The staff plays " + staffs.keySet() + "; name one");
    }
    return staffs.values().iterator().next();
  }

  public Collection<StaffPlayer> staffs() {
    return staffs.values();
  }

  public ApprovalGate gate() {
    return gate;
  }

  /** Files each step's orders with the staff player whose step it is. */
  public void setOrders(final List<PlannedStep> steps) {
    for (final PlannedStep step : steps) {
      staff(playerOf(step.step())).setOrders(step);
    }
  }

  private String playerOf(final String stepName) {
    for (final GameStep step : host.data().getSequence()) {
      if (stepName.equals(step.getName())) {
        if (step.getPlayerId() == null) {
          throw new IllegalArgumentException("Step " + stepName + " has no player");
        }
        return step.getPlayerId().getName();
      }
    }
    throw new IllegalArgumentException("Unknown step: " + stepName);
  }

  /** Runs the game on its own thread until the round passes {@code lastRound} or the game ends. */
  public void runInBackground(final int lastRound) {
    host.runInBackground(data -> data.getSequence().getRound() > lastRound);
  }

  /** Runs on the calling thread until the named step is current (or the game ends / cap is hit). */
  public int runUntilStep(final String stepName, final int maxSteps) {
    return host.runUntil(data -> data.getSequence().getStep().getName().equals(stepName), maxSteps);
  }

  /** Runs on the calling thread until the round passes {@code lastRound} (or the cap is hit). */
  public int runUntilRound(final int lastRound, final int maxSteps) {
    return host.runUntil(data -> data.getSequence().getRound() > lastRound, maxSteps);
  }

  public Status status() {
    final GameData data = host.data();
    final GamePlayer player = data.getSequence().getStep().getPlayerId();
    return new Status(
        data.getSequence().getRound(),
        data.getSequence().getStep().getName(),
        player == null ? "" : player.getName(),
        host.isRunning(),
        host.isGameOver(),
        gate.pending().orElse(null),
        staffs.values().stream().mapToInt(staff -> staff.records().size()).sum());
  }

  public AfterActionReport report() {
    return AfterActionReport.of(staffs.values(), gate, host.data(), host.isGameOver());
  }

  @Override
  public void close() {
    gate.release();
    host.close();
  }
}
