import java.util.*;
import java.util.stream.Collectors;
import javax.swing.text.html.Option;

/**
 * Grab the pellets as fast as you can!
 **/
class Player {

  public static Cell[][] cells;
  private List<String> commands = new ArrayList<>();
  private HashMap<Integer, Pair<Cell,Cell>> previousTargets = new HashMap<>();
  private Level level;
  private HashMap<Cell, Integer> superPacs = new HashMap<>();
  private boolean superPelletsLeft = false;
  private boolean startBoost = true;
  private boolean firstTurn = true;
  private int turnCount;

  public static void main(String args[]) {
    Player player = new Player();
    player.run();
  }

  private void run() {
    Scanner in = new Scanner(System.in);
    level = scanLevel(in);
    cells = new Cell[level.width][level.height];
    initialiseEmptyCells();
    executeGameLoop(in);
  }

  private void initialiseEmptyCells() {
    for (int y = 0; y < level.rows.size(); y++) {
      char[] charArr = level.rows.get(y).toCharArray();
      for (int x = 0; x < charArr.length; x++) {
        cellTypes cellType = charArr[x] == '#' ? cellTypes.WALL : cellTypes.FLOOR;
        Cell cell = new Cell(x, y, cellType);
        cells[x][y] = cell;
      }
    }
  }

  private void executeGameLoop(Scanner in) {
    while (true) {
      long start = System.currentTimeMillis();
      clearPacs();
      commands = new ArrayList<>();
      Frame frame = scanFrame(in);
      printMaze();
      calculateMoveBfs(level, frame);
      startBoost = false;
      firstTurn = false;
      System.out.println(String.join(" | ", commands));
      turnCount++;
      long end = System.currentTimeMillis();
      System.err.println(String.format("Turn calculation took %dms", end-start));
    }
  }

  private void printMaze() {
    System.err.println("Game Maze:");
    StringBuilder sb = new StringBuilder();
    for (int y = 0; y < level.rows.size(); y++) {
      char[] charArr = level.rows.get(y).toCharArray();
      for (int x = 0; x < charArr.length; x++) {
        Cell cell = cells[x][y];
        if (cell.isWall()) {
          sb.append('#');
        } else if (cell.pac.isPresent() && cell.pac.get().mine) {
          sb.append('M');
        } else if (cell.pac.isPresent()) {
          sb.append('E');
        } else if (cell.pellet.isPresent() && cell.pellet.get().value == 1) {
          sb.append('p');
        } else if (cell.pellet.isPresent() && cell.pellet.get().value == 10) {
          sb.append("P");
        } else if (!cell.seen) {
          sb.append("?");
        } else {
          sb.append("·");
        }
      }
      sb.append("\n");
    }
    System.err.println(sb.toString());
  }

  private void clearPacs() {
    for (int y = 0; y < level.rows.size(); y++) {
      char[] charArr = level.rows.get(y).toCharArray();
      for (int x = 0; x < charArr.length; x++) {
        Cell cell = cells[x][y];
        cell.pac = Optional.empty();
        cell.reserved = false;
        if (cell.pellet.isPresent() && cell.pellet.get().value == 10) {
          cell.pellet = Optional.empty();
        }
      }
    }
  }

  public Player() {
  }

  private void calculateMoveBfs(Level level, Frame frame) {
    for (Pac pac : frame.pacs) {
      if (!pac.mine || pac.typeId.equals("DEAD")) {
        continue;
      }
      Cell c = cells[pac.x][pac.y];
      c.reserved = true;
    }
    for (Pac pac : frame.pacs) {
      if (!pac.mine || pac.typeId.equals("DEAD")) {
        continue;
      }
      if (previousTargets.get(pac.id) != null && previousTargets.get(pac.id).left.x == pac.x && previousTargets.get(pac.id).left.y == pac.y) {
        cells[previousTargets.get(pac.id).right.x][previousTargets.get(pac.id).right.y].reserved = true;
      }
      List<MoveType> priorityCmd = new ArrayList<>();
      List<MoveType> cmd = new ArrayList<>();
      List<MoveType> superPellets = new ArrayList<>();
      List<MoveType> two = new ArrayList<>();
      setAllCellsToUnvisited();
      Queue<Pair<Cell, RouteMeta>> queue = new LinkedList<>();
      Cell pacCell = cells[pac.x][pac.y];
      pacCell.setPellet(Optional.empty());
      pacCell.reserved = true;
      RouteMeta rm = new RouteMeta(0, 0, new ArrayList<>());
      queue.add(new Pair<>(pacCell, rm));
      boolean danger = false;
      while (!queue.isEmpty()) {
        Pair<Cell, RouteMeta> pair = queue.remove();
        RouteMeta routeMeta = new RouteMeta(pair.right);
        Cell cell = pair.left;
        cell.visited = true;
        if (cell.x == 3 && cell.y == 5 && pac.id == 0) {
          System.err.println(cell);
//          System.err.println(routeMeta);
        }
        if ((routeMeta.steps == 1 || (routeMeta.steps == 2 && pac.speedTurnsLeft > 0)) && pac.abilityCooldown != 0 && cell.danger && turnCount - cell.dangerTurn <= 1 && !beatsOrTies(pac.typeId, cell.dangerType)) {
          System.err.println(String.format("Pac %d whossed out cell: %s", pac.id, cell));
          continue;
        }
        if ((routeMeta.steps == 1 || (routeMeta.steps == 2 && pac.speedTurnsLeft > 0)) && pac.abilityCooldown != 0 && cell.pac.isPresent() && !cell.pac.get().mine && cell.pac.get().abilityCooldown == 0) {
          System.err.println(String.format("Pac %d whossed out (enemy could change) cell: %s", pac.id, cell));
          continue;
        }
        //TODO prioritise pellets over unseen
        if ((cell.pellet.isPresent() || !cell.seen) && !cell.reserved && (!cell.danger || turnCount - cell.dangerTurn > 1  || (turnCount - cell.dangerTurn <= 1 && beatsOrTies(pac.typeId, cell.dangerType)))) {

          if (cell.pellet.isPresent() && cell.pellet.get().value == 10 && superPacs.get(cell) != null && superPacs.get(cell) == pac.id) {
            RouteMeta rm2 = new RouteMeta(routeMeta);
            if (routeMeta.steps == 1) {
              rm2.route.add(cell);
              rm2.route.add(findAdjacentPellet(cell));
              rm2.steps++;
              rm2.total++;
            } else {
              rm2.route.add(cell);
            }
            superPellets.add(new Move(pac.id, rm2));
          }
          if (routeMeta.steps == 2 && routeMeta.total > 1) {
            two.add(new Move(pac.id, routeMeta));
          }
          cmd.add(new Move(pac.id, routeMeta));
        }
        if (cell.pac.isPresent() && !cell.pac.get().mine && beats(pac.typeId, cell.pac.get().typeId) && routeMeta.steps == 1 && cell.pac.get().abilityCooldown > 1) {
          routeMeta.route.add(cell);
          priorityCmd.add(new Move(pac.id, routeMeta));
          break;
        }
        if (firstTurn && cell.pac.isPresent() && !cell.pac.get().mine && beats(pac.typeId, cell.pac.get().typeId) && routeMeta.steps == 1) {
          routeMeta.route.add(cell);
          priorityCmd.add(new Move(pac.id, routeMeta));
          break;
        }
        if (pac.abilityCooldown == 0 && routeMeta.steps == 0 && (!cell.danger || turnCount - cell.dangerTurn > 1)) {
            System.err.println(cell);
            priorityCmd.add(new Speed(pac.id));
            break;
        }
        if (routeMeta.steps > 4 && cmd.size() > 0 && (!superPelletsLeft || superPacs.entrySet().stream().filter(e -> e.getValue() == pac.id).noneMatch(e -> e.getKey().pellet.isPresent()))) { //need special routing to remaining super pellet
          if (pac.id == 0) {
            System.err.println("HUH!? " + pac);
          }
          break;
        }
        if (cell.pac.isPresent() && !cell.pac.get().isMine() && routeMeta.steps == 1 && pac.abilityCooldown == 0 && cell.pac.get().abilityCooldown != 0) {
          aggressiveStance(pac, priorityCmd, cell);
          break;
        }
        if (cell.pac.isPresent() && !cell.pac.get().isMine() && routeMeta.steps == 1 && pac.abilityCooldown == 0 && cell.pac.get().abilityCooldown == 0) {
          sneakyStance(pac, priorityCmd);
          break;
        }
        if (cell.pac.isPresent() && !cell.pac.get().isMine() && routeMeta.steps == 2 && pac.abilityCooldown == 0 && cell.pac.get().abilityCooldown == 0 && !beats(pac.typeId, cell.pac.get().typeId) && routeMeta.route.get(1).pellet.isPresent()) {
          List<Cell> currentPos = new ArrayList<>();
          currentPos.add(pacCell);
          currentPos.add(pacCell);
          Move move = new Move(pac.id, new RouteMeta(0, 0, currentPos));
          priorityCmd.add(move);
          break;
        }
        if (cell.pac.isPresent() && routeMeta.steps > 0) {
          continue;
        }
        List<Cell> cellsToQueue = new ArrayList<>();
        if (cell.x == 0) {
          Cell leftWrappedCell = cells[level.width - 1][cell.y];
          cellsToQueue.add(leftWrappedCell);
        } else {
          Cell leftCell = cells[cell.x - 1][cell.y];
          cellsToQueue.add(leftCell);
        }
        if (cell.x == level.width - 1) {
          Cell rightWrappedCell = cells[0][cell.y];
          cellsToQueue.add(rightWrappedCell);
        } else {
          Cell rightCell = cells[cell.x + 1][cell.y];
          cellsToQueue.add(rightCell);
        }
        Cell downCell = cells[cell.x][cell.y + 1];
        Cell upCell = cells[cell.x][cell.y - 1];
        cellsToQueue.add(downCell);
        cellsToQueue.add(upCell);
        routeMeta.steps += 1;
        routeMeta.route.add(cell);
        if (cell.pellet.isPresent()) {
          routeMeta.total++;
        } else if (!cell.seen) {
          routeMeta.total += 1.5;
        }
        //Collections.shuffle(cellsToQueue);
        for (Cell c: cellsToQueue) {
          boolean myPacWins = !c.pac.isPresent() || pac.abilityCooldown == 0 || beatsOrTies(pac.typeId, c.pac.get().typeId);
          boolean dangerOk = !c.danger || turnCount - c.dangerTurn > 1 || beatsOrTies(pac.typeId, c.dangerType);
          if (!c.isVisited() && !c.isWall() && !c.reserved) {
          //if (!c.isVisited() && !c.isWall() && !c.reserved && myPacWins && dangerOk) {
            //System.err.println(String.format("%d,%d is %d steps away from pac %d at %d,%d", c.x, c.y, routeMeta.steps, pac.id, pacCell.x, pacCell.y));
            queue.add(new Pair<>(c, routeMeta));
          }
        }
      }
      if (priorityCmd.size() > 0 && !(priorityCmd.get(0) instanceof Move)) {
        if (priorityCmd.get(0) instanceof Switch) {
          Switch switchMove = (Switch) priorityCmd.get(0);
          System.err.println(String.format("Pac %d switching to %s", switchMove.pacId, switchMove.pacType));
          commands.add(String.format("SWITCH %d %s", switchMove.pacId, switchMove.pacType));
        }
        if (priorityCmd.get(0) instanceof Speed) {
          Speed speed = (Speed) priorityCmd.get(0);
          System.err.println(String.format("Pac %d activating speed", speed.pacId));
          commands.add(String.format("SPEED %d", speed.pacId));
        }
      } else {
        Move move;
        if (priorityCmd.size() > 0) {
          move = (Move) priorityCmd.get(0);
          Cell lastStop = move.routeMeta.route.get(move.routeMeta.route.size() - 1);
          System.err.println(String.format("Pac %d making priority move to %d,%d", pac.id, lastStop.x, lastStop.y));
        } else if (superPellets.size() > 0) {
          move = (Move) superPellets.get(0);
          Cell lastStop = move.routeMeta.route.get(move.routeMeta.route.size() - 1);
          System.err.println(String.format("Pac %d going to super pellet at %d,%d", pac.id, lastStop.x, lastStop.y));
        } else if (two.size() > 0 && pac.speedTurnsLeft > 0) {
          two.sort((a, b) -> {
            Move m1 = (Move) a;
            Move m2 = (Move) b;
            return Double.compare(m2.routeMeta.total, m1.routeMeta.total);
          });
          move = (Move) two.get(0);
          Cell lastStop = move.routeMeta.route.get(move.routeMeta.route.size() - 1);
          System.err.println(String.format("Pac %d moving two to pellet at %d,%d potential profit: %s", pac.id, lastStop.x, lastStop.y, move.routeMeta.total));
        } else if (cmd.size() > 0){
          cmd.sort((a, b) -> {
            Move m1 = (Move) a;
            Move m2 = (Move) b;
            return Double.compare(m2.routeMeta.total, m1.routeMeta.total);
          });
          move = (Move) cmd.get(0);
          Cell lastStop = move.routeMeta.route.get(move.routeMeta.route.size() - 1);
          Cell firstStop;
          if (pac.speedTurnsLeft > 0 && move.routeMeta.route.size() > 2) {
            firstStop = move.routeMeta.route.get(2);
          } else {
            firstStop = move.routeMeta.route.get(1);
          }
          System.err.println(String.format("Pac %d moving to pellet at %d,%d (via %d,%d) potential profit: %s", pac.id, lastStop.x, lastStop.y, firstStop.x, firstStop.y, move.routeMeta.total));
        } else {
          System.err.println(String.format("Pac %d staying put, no commands", pac.id));
          List<Cell> currentPos = new ArrayList<>();
          currentPos.add(pacCell);
          currentPos.add(pacCell);
          move = new Move(pac.id, new RouteMeta(0, 0, currentPos));
        }
        Cell reservedCell = move.routeMeta.route.get(1);
        cells[reservedCell.x][reservedCell.y].reserved = true;
        if (pac.speedTurnsLeft > 0 && move.routeMeta.route.size() >= 3) {
        //if (pac.speedTurnsLeft > 0 && move.routeMeta.route.size() >= 3 && !isIntersection(move.routeMeta.route.get(1))) {
          Cell reservedCell2 = move.routeMeta.route.get(2);
          cells[reservedCell2.x][reservedCell2.y].reserved = true;
          queueCommand(pac, reservedCell2);
        } else {
          queueCommand(pac, reservedCell);
        }
      }
    }
  }

  private void queueCommand(Pac pac, Cell reservedCell) {
//    if (previousTargets[pac.id] != null && previousTargets[pac.id].x == reservedCell.x && previousTargets[pac.id].y == reservedCell.y && pac.abilityCooldown == 0) {
//      if (pac.typeId.equals("ROCK")) {
//        commands.add(String.format("SWITCH %d %s", pac.id, "PAPER"));
//      } else if (pac.typeId.equals("SCISSORS")) {
//        commands.add(String.format("SWITCH %d %s", pac.id, "ROCK"));
//      } else if (pac.typeId.equals("PAPER")) {
//        commands.add(String.format("SWITCH %d %s", pac.id, "SCISSORS"));
//      }
//      return;
//    }
    previousTargets.put(pac.id, new Pair<>(cells[pac.x][pac.y], reservedCell));
    commands.add(String.format("MOVE %d %d %d %s", pac.getId(), reservedCell.x, reservedCell.y, pac.id));
  }

  public void banEnemySuperPellets(Cell superPellet, boolean onlyOne) {
    setAllCellsToUnvisited();
    Queue<Pair<Cell, Integer>> queue = new LinkedList<>();
    queue.add(new Pair<>(superPellet, 0));
    int closestEnemy = Integer.MAX_VALUE;
    while (!queue.isEmpty()) {
      Pair<Cell, Integer> pair = queue.remove();
      Cell cell = pair.left;
      cell.visited = true;
      if (closestEnemy < pair.right) {
        cells[superPellet.x][superPellet.y].pellet = Optional.empty();
        System.err.println(String.format("Banned super pellet at %d,%d", superPellet.x, superPellet.y));
        return;
      }
      if (cell.pac.isPresent()) {
        if (!cell.pac.get().mine) {
          closestEnemy = Math.min(closestEnemy, pair.right);
        } else {
          cells[superPellet.x][superPellet.y].superPelletPac = cell.pac;
          cell.pac.get().hasSuperPellet = true;
          superPacs.put(cells[superPellet.x][superPellet.y], cell.pac.get().id);
          System.err.println(String.format("Assigning pac %d to superPellet at %d, %d", cell.pac.get().id, superPellet.x, superPellet.y));
          return;
        }
      }
      List<Cell> cellsToQueue = new ArrayList<>();
      if (cell.x == 0) {
        Cell leftWrappedCell = cells[level.width - 1][cell.y];
        cellsToQueue.add(leftWrappedCell);
      } else {
        Cell leftCell = cells[cell.x - 1][cell.y];
        cellsToQueue.add(leftCell);
      }
      if (cell.x == level.width - 1) {
        Cell rightWrappedCell = cells[0][cell.y];
        cellsToQueue.add(rightWrappedCell);
      } else {
        Cell rightCell = cells[cell.x + 1][cell.y];
        cellsToQueue.add(rightCell);
      }
      Cell downCell = cells[cell.x][cell.y + 1];
      Cell upCell = cells[cell.x][cell.y - 1];
      cellsToQueue.add(downCell);
      cellsToQueue.add(upCell);
      for (Cell c: cellsToQueue) {
        if (!c.isVisited() && !c.isWall()) {
          queue.add(new Pair<>(c, pair.right + 1));
        }
      }
    }
  }

  public void setDanger(Pac pac) {
    setAllCellsToUnvisited();
    Queue<Pair<Cell, Integer>> queue = new LinkedList<>();
    queue.add(new Pair<>(cells[pac.x][pac.y], 0));
    while (!queue.isEmpty()) {
      Pair<Cell, Integer> pair = queue.remove();
      Cell cell = pair.left;
      int count = pair.right;
      cell.visited = true;
      if (pac.speedTurnsLeft > 0 && count > 2 || pac.speedTurnsLeft == 0 && count > 1) {
        continue;
      }
      cell.danger = true;
      cell.dangerTurn = turnCount;
      cell.dangerType = pac.typeId;
      if (count == 2) {
        cell.speedDanger = true;
      }
      if(cell.pac.isPresent() && cell.pac.get().mine) {
        continue;
      }
      List<Cell> cellsToQueue = new ArrayList<>();
      if (cell.x == 0) {
        Cell leftWrappedCell = cells[level.width - 1][cell.y];
        cellsToQueue.add(leftWrappedCell);
      } else {
        Cell leftCell = cells[cell.x - 1][cell.y];
        cellsToQueue.add(leftCell);
      }
      if (cell.x == level.width - 1) {
        Cell rightWrappedCell = cells[0][cell.y];
        cellsToQueue.add(rightWrappedCell);
      } else {
        Cell rightCell = cells[cell.x + 1][cell.y];
        cellsToQueue.add(rightCell);
      }
      Cell downCell = cells[cell.x][cell.y + 1];
      Cell upCell = cells[cell.x][cell.y - 1];
      cellsToQueue.add(downCell);
      cellsToQueue.add(upCell);

      for (Cell c: cellsToQueue) {
        if (!c.isVisited() && !c.isWall()) {
          queue.add(new Pair<>(c, count + 1));
        }
      }
    }
  }

  private void sneakyStance(Pac pac, List<MoveType> priorityCmd) {
    if (pac.typeId.equals("ROCK")) {
      priorityCmd.add(new Switch(pac.id, "SCISSORS"));
    } else if (pac.typeId.equals("SCISSORS")) {
      priorityCmd.add(new Switch(pac.id, "PAPER"));
    } else if (pac.typeId.equals("PAPER")) {
      priorityCmd.add(new Switch(pac.id, "ROCK"));
    }
  }

  private void aggressiveStance(Pac pac, List<MoveType> priorityCmd, Cell cell) {
    if ("SCISSORS".equals(cell.pac.get().typeId) && !pac.typeId.equals("ROCK")) {
      priorityCmd.add(new Switch(pac.id, "ROCK"));
    } else if ("PAPER".equals(cell.pac.get().typeId) && !pac.typeId.equals("SCISSORS")) {
      priorityCmd.add(new Switch(pac.id, "SCISSORS"));
    } else if ("ROCK".equals(cell.pac.get().typeId) && !pac.typeId.equals("PAPER")) {
      priorityCmd.add(new Switch(pac.id, "PAPER"));
    }
  }

  private boolean beats(String type1, String type2) {
    return (type1.equals("SCISSORS") && type2.equals("PAPER")) ||
        (type1.equals("PAPER") && type2.equals("ROCK")) ||
        type1.equals("ROCK") && type2.equals("SCISSORS");
  }

  private boolean beatsOrTies(String type1, String type2) {
    return (type1.equals("SCISSORS") && type2.equals("PAPER")) ||
        (type1.equals("PAPER") && type2.equals("ROCK")) ||
        (type1.equals("ROCK") && type2.equals("SCISSORS")) ||
        (type1.equals(type2));
  }

  private void setAllCellsToUnvisited() {
    for (Cell[] row : cells) {
      for (Cell c : row) {
        c.visited = false;
      }
    }
  }

  private Frame scanFrame(Scanner in) {
    int myScore = in.nextInt();
    int opponentScore = in.nextInt();
    List<Pac> pacs = new ArrayList<>();
    List<Pellet> pellets = new ArrayList<>();
    int visiblePacCount = in.nextInt(); // all your pacs and enemy pacs in sight
    for (int i = 0; i < visiblePacCount; i++) {
      int pacId = in.nextInt(); // pac number (unique within a team)
      boolean mine = in.nextInt() != 0; // true if this pac is yours
      int x = in.nextInt(); // position in the grid
      int y = in.nextInt(); // position in the grid
      String typeId = in.next(); // unused in wood leagues
      int speedTurnsLeft = in.nextInt(); // unused in wood leagues
      int abilityCooldown = in.nextInt(); // unused in wood leagues
      Pac pac = new Pac(pacId, mine, x, y, typeId, speedTurnsLeft, abilityCooldown, false);
      if (pac.typeId.equals("DEAD")) {
        continue;
      }
      if (pac.mine) {
        if (firstTurn) {
          loadSymmetricalEnemyPac(pac);
        }
        leftPellets(cells[x][y], 0);
        rightPellets(cells[x][y], 0);
        upPellets(cells[x][y], 0);
        downPellets(cells[x][y], 0);
      }
      cells[x][y].pac = Optional.of(pac);
      pacs.add(pac);
    }
    for (Pac pac: pacs) {
      if (!pac.mine) {
        setDanger(pac);
      }
    }
    int visiblePelletCount = in.nextInt(); // all pellets in sight
    for (int i = 0; i < visiblePelletCount; i++) {
      int x = in.nextInt();
      int y = in.nextInt();
      int value = in.nextInt(); // amount of points this pellet is worth
      Pellet pellet = new Pellet(x, y, value);
      pellets.add(pellet);
    }
    List<Pac> myPacs = pacs.stream().filter(pac -> pac.mine).collect(Collectors.toList());
    List<Pellet> superPellets = pellets.stream().filter(p -> p.value == 10).collect(Collectors.toList());
    superPelletsLeft = false;
    for (Pellet pellet: pellets) {
      int x = pellet.x;
      int y = pellet.y;
      if (pellet.value == 10) {
        cells[x][y].seen = true;
        superPelletsLeft = true;
        cells[x][y].pellet = Optional.of(pellet);
        if (firstTurn) {
          banEnemySuperPellets(cells[x][y], myPacs.size() >= superPellets.size());
        }
      } else {
        cells[x][y].pellet = Optional.of(pellet);
      }
    }
    return new Frame(myScore, opponentScore, pacs, pellets);
  }

  private void loadSymmetricalEnemyPac(Pac pac) {
    int width = level.width - 1;
    Pac enemyPac = new Pac(pac);
    enemyPac.mine = false;
    enemyPac.x = width - pac.x;
    cells[enemyPac.x][enemyPac.y].pac = Optional.of(enemyPac);
  }

  public Cell findAdjacentPellet(Cell superPellet) {
    System.err.println("BLAH BLAH BLAH: " + superPellet);
    int x = superPellet.x;
    int y = superPellet.y;
    Cell left;
    Cell right;
    if (x == 0) {
      left = cells[level.width - 1][y];
      right = cells[x+1][y];
    } else if (x == level.width -1) {
      left = cells[x-1][y];
      right = cells[0][y];
    } else {
      left = cells[x-1][y];
      right = cells[x+1][y];
    }
    Cell up = cells[x][y-1];
    Cell down = cells[x][y+1];
    List<Cell> bleh = new ArrayList<>();
    bleh.add(left);
    bleh.add(right);
    bleh.add(up);
    bleh.add(down);
    for (Cell c: bleh) {
      System.err.println("HOE: " + c);
      if ((c.pellet.isPresent() || (!c.isWall() && !c.seen))) {
        System.err.println("Returning: " + c);
        return c;
      }
    }
    return superPellet;
  }

  public void leftPellets(Cell cell, int count) {
    if (count > level.width) {
      return;
    }
    if (!cell.isWall()) {
      if (cell.pellet.isPresent()) {
        cell.pellet = Optional.empty();
      }
      cell.seen = true;
      if (cell.x == 0) {
        leftPellets(cells[level.width - 1][cell.y], count + 1);
      } else {
        leftPellets(cells[cell.x - 1][cell.y], count + 1);
      }
    }
  }

  private void rightPellets(Cell cell, int count) {
    if (count > level.width) {
      return;
    }
    if (!cell.isWall()) {
      if (cell.pellet.isPresent()) {
        cell.pellet = Optional.empty();
      }
      cell.seen = true;
      if (cell.x == level.width - 1) {
        rightPellets(cells[0][cell.y], count + 1);
      } else {
        rightPellets(cells[cell.x + 1][cell.y], count + 1);
      }
    }
  }

  private void upPellets(Cell cell, int count) {
    if (!cell.isWall()) {
      if (cell.pellet.isPresent()) {
        cell.pellet = Optional.empty();
      }
      cell.seen = true;
      upPellets(cells[cell.x][cell.y - 1], count + 1);
    }
  }

  private void downPellets(Cell cell, int count) {
    if (!cell.isWall()) {
      if (cell.pellet.isPresent()) {
        cell.pellet = Optional.empty();
      }
      cell.seen = true;
      downPellets(cells[cell.x][cell.y + 1], count + 1);
    }
  }

  private Level scanLevel(Scanner in) {
    int width = in.nextInt(); // size of the grid
    int height = in.nextInt(); // top left corner is (x=0, y=0)
    List<String> rows = new ArrayList<>();
    if (in.hasNextLine()) {
      in.nextLine();
    }
    for (int i = 0; i < height; i++) {
      rows.add(in.nextLine()); // one line of the grid: space " " is floor, pound "#" is wall
    }
    return new Level(width, height, rows);
  }

  private final class Frame {

    private final int myScore;
    private final int enemyScore;
    private final List<Pac> pacs;
    private final List<Pellet> pellets;

    public Frame(int myScore, int enemyScore, List<Pac> pacs, List<Pellet> pellets) {
      this.myScore = myScore;
      this.enemyScore = enemyScore;
      this.pacs = pacs;
      this.pellets = pellets;
    }

    public int getMyScore() {
      return myScore;
    }

    public int getEnemyScore() {
      return enemyScore;
    }

    public List<Pac> getPacs() {
      return pacs;
    }

    public List<Pellet> getPellets() {
      return pellets;
    }
  }

  private final class Pellet {
    private final int x;
    private final int y;
    private final int value;

    public Pellet(int x, int y, int value) {
      this.x = x;
      this.y = y;
      this.value = value;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public int getValue() {
      return value;
    }
  }

  private final class Pac {
    private final int id;
    private boolean mine;
    private int x;
    private final int y;
    private final String typeId;
    private final int speedTurnsLeft;
    private final int abilityCooldown;
    private boolean hasSuperPellet;

    public Pac(int id, boolean mine, int x, int y, String typeId, int speedTurnsLeft, int abilityCooldown, boolean hasSuperPellet) {
      this.id = id;
      this.mine = mine;
      this.x = x;
      this.y = y;
      this.typeId = typeId;
      this.speedTurnsLeft = speedTurnsLeft;
      this.abilityCooldown = abilityCooldown;
      this.hasSuperPellet = hasSuperPellet;
    }

    public Pac(Pac pac) {
      this.id = pac.id;
      this.mine = pac.mine;
      this.x = pac.x;
      this.y = pac.y;
      this.typeId = pac.typeId;
      this.speedTurnsLeft = pac.speedTurnsLeft;
      this.abilityCooldown = pac.abilityCooldown;
      this.hasSuperPellet = pac.hasSuperPellet;
    }

    public int getId() {
      return id;
    }

    public boolean isMine() {
      return mine;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public String getTypeId() {
      return typeId;
    }

    public int getSpeedTurnsLeft() {
      return speedTurnsLeft;
    }

    public int getAbilityCooldown() {
      return abilityCooldown;
    }

    @Override
    public String toString() {
      return "Pac{" +
          "id=" + id +
          ", mine=" + mine +
          ", x=" + x +
          ", y=" + y +
          ", typeId='" + typeId + '\'' +
          ", speedTurnsLeft=" + speedTurnsLeft +
          ", abilityCooldown=" + abilityCooldown +
          '}';
    }
  }

  private class Level {
    private final int width;
    private final int height;
    private final List<String> rows;

    public Level(int width, int height, List<String> rows) {
      this.width = width;
      this.height = height;
      this.rows = Collections.unmodifiableList(rows);
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }

    public List<String> getRows() {
      return rows;
    }
  }

  private class Cell {
    private final int x;
    private final int y;
    private final cellTypes cellType;
    private Optional<Pac> pac = Optional.empty();
    private Optional<Pellet> pellet = Optional.empty();
    private boolean visited;
    private boolean reserved;
    private boolean seen;
    private boolean danger;
    private String dangerType;
    private int dangerTurn;
    private boolean speedDanger;
    private Optional<Pac> superPelletPac = Optional.empty();

    public Cell(int x, int y, cellTypes cellType) {
      this.x = x;
      this.y = y;
      this.cellType = cellType;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public cellTypes getCellType() {
      return cellType;
    }

    public boolean isVisited() {
      return visited;
    }

    public void setVisited(boolean visited) {
      this.visited = visited;
    }

    public Optional<Pac> getPac() {
      return pac;
    }

    public void setPac(Optional<Pac> pac) {
      this.pac = pac;
    }

    public Optional<Pellet> getPellet() {
      return pellet;
    }

    public void setPellet(Optional<Pellet> pellet) {
      this.pellet = pellet;
    }

    public boolean isWall() {
      return cellType.equals(cellTypes.WALL);
    }

    @Override
    public String toString() {
      return "Cell{" +
          "x=" + x +
          ", y=" + y +
          ", cellType=" + cellType +
          ", pac=" + pac +
          ", pellet=" + pellet +
          ", visited=" + visited +
          ", reserved=" + reserved +
          ", seen=" + seen +
          ", danger=" + danger +
          ", dangerType='" + dangerType + '\'' +
          ", dangerTurn=" + dangerTurn +
          '}';
    }
  }

  private enum cellTypes {
    FLOOR,
    WALL
  }

  private class RouteMeta {
    int steps;
    double total;
    List<Cell> route;

    public RouteMeta(RouteMeta routeMeta) {
      this.steps = routeMeta.steps;
      this.total = routeMeta.total;
      this.route = new ArrayList<>(routeMeta.route);
    }

    public RouteMeta(int steps, int total, List<Cell> route) {
      this.steps = steps;
      this.total = total;
      this.route = route;
    }

    @Override
    public String toString() {
      return "RouteMeta{" +
          "steps=" + steps +
          ", total=" + total +
          ", route=" + route +
          '}';
    }
  }

  private class Pair<L, R> {
    private final L left;
    private final R right;

    public Pair(L left, R right) {
      this.left = left;
      this.right = right;
    }
  }

  private class Speed implements MoveType {
    int pacId;

    public Speed(int pacId) {
      this.pacId = pacId;
    }
  }

  private class Switch implements MoveType {
    int pacId;
    String pacType;

    public Switch(int pacId, String pacType) {
      this.pacId = pacId;
      this.pacType = pacType;
    }
  }

  private class Move implements MoveType {
    int pacId;
    RouteMeta routeMeta;

    public Move(int pacId, RouteMeta routeMeta) {
      this.pacId = pacId;
      this.routeMeta = routeMeta;
    }
  }

  private interface MoveType {

  }
}