import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;


public class ProbabilisticSearchAgent extends Agent {
	//Approximation of the chances a random square has a tower
	private Double pTower = .00616;
	private Double pShotGivenTower = .75;
	private Double[][] towerProbs;
	private Double[][] knownTiles;
	private Boolean[][] occupied;
	private Boolean[][] visited;
	private Map<Integer, Integer> peasants;
	private Double UP_WEIGHTING = 4.0;
	private Double RIGHT_WEIGHTING = 4.0;
	private Double EXPLORATION_WEIGHTING = 3.5;

	public ProbabilisticSearchAgent(int playernum) {
		super(playernum);
	}

	@Override
	public Map<Integer, Action> initialStep(StateView state, HistoryView history) {
		towerProbs = new Double[state.getXExtent()][state.getYExtent()];
		knownTiles = new Double[state.getXExtent()][state.getYExtent()];
		occupied = new Boolean[state.getXExtent()][state.getYExtent()];
		visited = new Boolean[state.getXExtent()][state.getYExtent()];
		peasants = new HashMap<Integer, Integer>();

		for (int x = 0; x < towerProbs.length; x++) {
			for (int y = 0; y < towerProbs[x].length; y++) {
				towerProbs[x][y] = pTower;
				knownTiles[x][y] = null;
				occupied[x][y] = false;
				visited[x][y] = false;
			}
		}
		
		for (UnitView unit : state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				peasants.put(unit.getID(), unit.getHP());
			}
			
			if (unit.getTemplateView().getName().equalsIgnoreCase("TownHall")) {
				occupied[unit.getXPosition()][unit.getYPosition()] = true;
				knownTiles[unit.getXPosition()][unit.getYPosition()] = 0.0;
			}
		}

		return null;
	}

	@Override
	public void loadPlayerData(InputStream arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<Integer, Action> middleStep(StateView state, HistoryView history) {
		Map<Integer, Action> toReturn = new HashMap<Integer, Action>();
		
		Set<UnitView> injured = checkInjuries(state);
		Set<UnitView> uninjured = getUninjured(state);
		
		for (UnitView peasant : injured) {
			System.out.println("!!!!                   SHOT                !!!!");
			for (int x = peasant.getXPosition() - 4; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y >= 0) {
//							Double temp = towerProbs[x][y];
							towerProbs[x][y] = (pShotGivenTower*towerProbs[x][y])/getPShot(peasant, state);
//							temp = towerProbs[x][y];
//							temp += 0;
//							towerProbs[x][y] *= 5;
						}
					}
				}
			}
			
			
			EXPLORATION_WEIGHTING = Math.pow(EXPLORATION_WEIGHTING, .1);
//			UP_WEIGHTING = Math.pow(UP_WEIGHTING, .1);
//			RIGHT_WEIGHTING = Math.pow(RIGHT_WEIGHTING, .1);
			
//			EXPLORATION_WEIGHTING = 0.1;
		}
		
		for (UnitView peasant : uninjured) {
			for (int x = peasant.getXPosition() - 4; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y >= 0) {
							towerProbs[x][y] = ((1 - pShotGivenTower)*towerProbs[x][y])/(1 - getPShot(peasant, state));
							
//							towerProbs[x][y] *= .25;
						}
					}
				}
			}
			
			EXPLORATION_WEIGHTING *= 2.5;
//			UP_WEIGHTING *= 1.2;
//			RIGHT_WEIGHTING *= 1.2;
		}
		
		/** Mark all Tower squares with max probability */
		for (UnitView unit: state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("ScoutTower") ||
				unit.getTemplateView().getName().equalsIgnoreCase("GuardTower")) {
				knownTiles[unit.getXPosition()][unit.getYPosition()] = 1.0;
				occupied[unit.getXPosition()][unit.getYPosition()] = true;
			}
		}
		
		/** Mark all Resource squares with zero probability */
		for (ResourceView resource: state.getAllResourceNodes()) {
			knownTiles[resource.getXPosition()][resource.getYPosition()] = .0;
			occupied[resource.getXPosition()][resource.getYPosition()] = true;
		}
		
		for (UnitView peasant : getAllPeasants(state)) {
			for (int x = peasant.getXPosition() - 2; x < peasant.getXPosition() + 3; x++) {
				if (x >= 0 && x < state.getXExtent()) {
					for (int y = peasant.getYPosition() - 2; y < peasant.getYPosition() + 3; y++) {
						if (y >= 0 && y < state.getYExtent()) {
							if (!occupied[x][y]) {
								knownTiles[x][y] = 0.0; // ??? this was 0.5 for some reason
							}
						}
					}
				}
			}
			
			visited[peasant.getXPosition()][peasant.getYPosition()] = true;
			occupied[peasant.getXPosition()][peasant.getYPosition()] = true;
		}
		
		for (UnitView peasant : getAllPeasants(state)) {
			int xTile = 0;
			int yTile = 0;
			Double probability = Double.MAX_VALUE;
			
			for (int x = peasant.getXPosition() - 1; x <= peasant.getXPosition() + 1; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 1; y <= peasant.getYPosition() + 1; y++) {
						if (y < state.getYExtent() && y >= 0 &&
								occupied[x][y] == false) {
							Double innerProbability = 0.0;
							System.out.print("Considering [" + x + ", " + y + "]: (");
							
							for (int innerX = x - 4; innerX < x + 5; innerX++) {
								if (innerX < state.getXExtent() && innerX >= 0) {
									for (int innerY = y - 4; innerY < y + 5; innerY++) {
										if (innerY < state.getYExtent() && innerY >= 0) {
											if (knownTiles[innerX][innerY] != null) {
												innerProbability += knownTiles[innerX][innerY];
												System.out.print(knownTiles[innerX][innerY] + " + ");
											} else {
												innerProbability += towerProbs[innerX][innerY];
												System.out.print(towerProbs[innerX][innerY] + " + ");
//												System.out.println("Tower Prob: " + towerProbs[innerX][innerY]);
											}
										} else {
											System.out.print(" OFF + ");
										}
									}
								} else {
									System.out.print(" OFF + ");
								}
								
								System.out.println();
							}
							
							System.out.print("0)");
							
//							innerProbability += (Math.abs(y - 0) + Math.abs(x - state.getXExtent()));
							
							if (x > peasant.getXPosition()) {
								innerProbability /= RIGHT_WEIGHTING;
								System.out.print(" / " + RIGHT_WEIGHTING);
							}
							
							if (y < peasant.getYPosition()) {
								innerProbability /= UP_WEIGHTING;
								System.out.print(" / " + UP_WEIGHTING);
							}
							
							if (!visited[x][y]) {
								innerProbability /= EXPLORATION_WEIGHTING;
								System.out.print(" / " + EXPLORATION_WEIGHTING);
							}
							
							System.out.println(" = " + innerProbability);
							
							if (innerProbability < probability) {
								probability = innerProbability;
								xTile = x;
								yTile = y;
							}
						}
					}
				}
			}
			
			for (UnitView unit : getAllPeasants(state)) {
				occupied[unit.getXPosition()][unit.getYPosition()] = false;
			}
			
			System.out.println(probability + " MOVE TO: " + xTile + ", " + yTile);
			
			toReturn.put(peasant.getID(), Action.createCompoundMove(peasant.getID(), xTile, yTile));
			
			printProbs(state, peasant);
		}	
		
		System.out.println("--------------------------------------------------------------------------------------------");
		
		return toReturn;
	}
	
	private Set<UnitView> getAllPeasants(StateView state) {
		Set<UnitView> toReturn = new HashSet<UnitView>();
		
		for (UnitView unit : state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				toReturn.add(unit);
			}
		}
		
		return toReturn;
	}
	
	private void printProbs(StateView state, UnitView unit) {
		System.out.println("-----------------------------------------------------------------------------------------");
		System.out.println("-----------------------------------------------------------------------------------------");
		for (int y = unit.getYPosition() - 7; y < unit.getYPosition() + 7; y++) {
			for (int x = unit.getXPosition() - 7; x < unit.getXPosition() + 7; x++) {
				if (x == unit.getXPosition() && y == unit.getYPosition()) {
					System.out.print(" [ (0^0) ] ");
				} else if (x >= 0 && x < state.getXExtent() &&
					y >= 0 && y < state.getYExtent()) {
					if (towerProbs[x][y] < .000005) {
						System.out.print(" [0.00000] ");
					} else {
						System.out.print(" [" + new DecimalFormat("#.#####").format(towerProbs[x][y]) + "] ");
					}
				} else {
					System.out.print(" [  OFF  ] ");
				}
			}
			System.out.println("  " + y);
			System.out.println();
			System.out.println();
		}
	}
	
	private Double getPShot(UnitView peasant, StateView state) {
		Double total = 0.0;
		
		for (int x = peasant.getXPosition() - 4; x <= peasant.getXPosition() + 5; x++) {
			if (x < state.getXExtent() && x >= 0) {
				for (int y = peasant.getYPosition() - 4; y <= peasant.getYPosition() + 5; y++) {
					if (y < state.getYExtent() && y >= 0) {
						total += towerProbs[x][y];
					}
				}
			}
		}
		
		return 1 - Math.pow(1 - pShotGivenTower, total);
	}
	
	private Set<UnitView> checkInjuries(StateView state) {
		Set<UnitView> injured = new HashSet<UnitView>();
		
		for (UnitView unit : state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				if (unit.getHP() < peasants.get(unit.getID())) {
					injured.add(unit);
					peasants.put(unit.getID(), unit.getHP());
				}
			}
		}
		
		return injured;
	}
	
	private Set<UnitView> getUninjured(StateView state) {
		Set<UnitView> uninjured = new HashSet<UnitView>();
		
		for (UnitView unit : state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				if (unit.getHP() >= peasants.get(unit.getID())) {
					uninjured.add(unit);
				}
			}
		}
		
		return uninjured;
	}

	@Override
	public void savePlayerData(OutputStream arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void terminalStep(StateView arg0, HistoryView arg1) {
		// TODO Auto-generated method stub

	}

}
