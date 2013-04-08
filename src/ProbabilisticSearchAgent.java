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
	private Double BIG_VALUE = 100.0;

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
			for (int x = peasant.getXPosition() - 5; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y >= 0) {
//							towerProbs[x][y] *= (pShotGivenTower*pTower)/getPShot(peasant, state);
							towerProbs[x][y] *= 20;
						}
					}
				}
			}
			
			visited[peasant.getXPosition()][peasant.getYPosition()] = true;
		}
		
		for (UnitView peasant : uninjured) {
			for (int x = peasant.getXPosition() - 5; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y >= 0) {
//							towerProbs[x][y] = ((1 - pShotGivenTower)*pTower)/
//									(((1 - pShotGivenTower)*pTower) + );
							towerProbs[x][y] *= .25;
						}
					}
				}
			}
		}
		
		/** Mark all Tower squares with max probability */
		for (UnitView unit: state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("ScoutTower") ||
				unit.getTemplateView().getName().equalsIgnoreCase("GuardTower")) {
				knownTiles[unit.getXPosition()][unit.getYPosition()] = BIG_VALUE;
				occupied[unit.getXPosition()][unit.getYPosition()] = true;
			}
		}
		
		/** Mark all Resource squares with zero probability */
		for (ResourceView resource: state.getAllResourceNodes()) {
			knownTiles[resource.getXPosition()][resource.getYPosition()] = .01;
			occupied[resource.getXPosition()][resource.getYPosition()] = true;
		}
		
		for (UnitView peasant : getAllPeasants(state)) {
			for (int x = peasant.getXPosition() - 3; x < peasant.getXPosition() + 3; x++) {
				if (x >= 0 && x < state.getXExtent()) {
					for (int y = peasant.getYPosition() - 3; y < peasant.getYPosition() + 3; y++) {
						if (y >= 0 && y < state.getYExtent()) {
							if (!occupied[x][y]) {
								knownTiles[x][y] = 0.0;
							}
						}
					}
				}
			}
			
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
							
							for (int innerX = x - 5; innerX < x + 5; innerX++) {
								if (innerX < state.getXExtent() && innerX >= 0) {
									for (int innerY = y - 5; innerY < y + 5; innerY++) {
										if (innerY < state.getYExtent() && innerY >= 0) {
											if (knownTiles[innerX][innerY] != null) {
												innerProbability += knownTiles[innerX][innerY];
											} else {
												innerProbability += towerProbs[innerX][innerY];
											}
										}
									}
								}
							}
							
//							innerProbability += (Math.abs(y - 0) + Math.abs(x - state.getXExtent()));
							
							if (x > peasant.getXPosition()) {
								innerProbability /= 5;
							}
							
							if (y < peasant.getYPosition()) {
								innerProbability /= 5;
							}
							
							if (!visited[x][y]) {
								innerProbability /= 5;
							}
							
							System.out.println(innerProbability + "    CONSIDERING: " + x + ", " + y);
							
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
	
	private void printProbs() {
		System.out.println("-----------------------------------------------------------------------------------------");
		System.out.println("-----------------------------------------------------------------------------------------");
		for (int x = 0; x < towerProbs.length; x++) {
			for (int y = 0; y < towerProbs[x].length; y++) {
				System.out.print("[" + new DecimalFormat("#.####").format(towerProbs[x][y]) + "] ");
			}
			
			System.out.println();
		}
	}
	
	private Double getPShot(UnitView peasant, StateView state) {
		Double total = 0.0;
		
		for (int x = peasant.getXPosition() - 5; x < peasant.getXPosition() + 5; x++) {
			if (x < state.getXExtent() && x > 0) {
				for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
					if (y < state.getYExtent() && y > 0) {
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
