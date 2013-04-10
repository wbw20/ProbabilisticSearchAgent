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


/**
 * This agent uses Bayesian distributions to cause an agent to search 
 * for a gold mine while avoiding enemy towers and then harvest gold
 * until the player has 2000 gold.  Unfortunately, heuristics are not
 * tuned properly, and so while the agent properly maintains the 
 * distributions, peasants are often caught in areas of low risk
 * indefinitely, or will traverse the board in spite of high risks
 * because a heuristic is too powerful and is ignoring the probability
 * distribution.
 * 
 * @author William Wettersten
 * @author William Cork
 *
 */
public class ProbabilisticSearchAgent extends Agent {
	//Approximation of the chances a random square has a tower
	private Double pTower = .00616;
	private Double pShotGivenTower = .75;
	
	//These track our probability distribution, as well as 
	//some other data like whether or not a given square
	//contains a tower.
	private Double[][] towerProbs;
	private Double[][] knownTiles;
	private Boolean[][] occupied;
	private Boolean[][] visited;
	private Map<Integer, Integer> peasants;
	
	// Heuristic weights initial values
	private static Double UP_WEIGHTING_INITIAL = 10.0;
	private static Double RIGHT_WEIGHTING_INITIAL = 10.0;
	private static Double EXPLORATION_WEIGHTING_INITIAL = 3.5;
	
	// Heuristic weights per peasant
	private static Map<Integer, Double> UP_WEIGHTING;
	private static Map<Integer, Double> RIGHT_WEIGHTING;
	private static Map<Integer, Double> EXPLORATION_WEIGHTING;

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
		
		UP_WEIGHTING = new HashMap<Integer, Double>();
		RIGHT_WEIGHTING = new HashMap<Integer, Double>();
		EXPLORATION_WEIGHTING = new HashMap<Integer, Double>();

		//Fill out our probability distribution with initial values
		for (int x = 0; x < towerProbs.length; x++) {
			for (int y = 0; y < towerProbs[x].length; y++) {
				towerProbs[x][y] = pTower;
				knownTiles[x][y] = null;
				occupied[x][y] = false;
				visited[x][y] = false;
			}
		}
		
		//Fill out our heuristic values with initial values
		for (UnitView unit : state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				peasants.put(unit.getID(), unit.getHP());
				UP_WEIGHTING.put(unit.getID(), UP_WEIGHTING_INITIAL);
				RIGHT_WEIGHTING.put(unit.getID(), RIGHT_WEIGHTING_INITIAL);
				EXPLORATION_WEIGHTING.put(unit.getID(), EXPLORATION_WEIGHTING_INITIAL);
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

	/**
	 * The middle step works by first taking an inventory of who was injured during the
	 * last turn.  For those injured, the probability that there is a tower increases
	 * for each cell in a 5 square radius.  For those not injured, the probability
	 * that there is a tower decreases for all squares in a 5 square radius.  Then,
	 * A utility function is calculated for each possible move of each peasant, and the move
	 * with the lowest utilitty function is taken.
	 */
	@Override
	public Map<Integer, Action> middleStep(StateView state, HistoryView history) {
		Map<Integer, Action> toReturn = new HashMap<Integer, Action>();
		
		Set<UnitView> injured = checkInjuries(state);
		Set<UnitView> uninjured = getUninjured(state);
		
		//If a peasant was injured, we use Bayes' Theorem to increase the probability of a tower
		//near where the peasant was shot.  Also, we make our agent more cautious by reweighting
		//some of our heuristics.
		for (UnitView peasant : injured) {
			for (int x = peasant.getXPosition() - 4; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y >= 0) {
							towerProbs[x][y] = (pShotGivenTower*towerProbs[x][y])/getPShot(peasant, state);
						}
					}
				}
			}
			
			EXPLORATION_WEIGHTING.put(peasant.getID(), Math.signum(EXPLORATION_WEIGHTING.get(peasant.getID())) * 
					Math.pow(EXPLORATION_WEIGHTING.get(peasant.getID()), .1));
			UP_WEIGHTING.put(peasant.getID(),  Math.signum(UP_WEIGHTING.get(peasant.getID())) * 
					Math.pow(UP_WEIGHTING.get(peasant.getID()), .02));
			RIGHT_WEIGHTING.put(peasant.getID(),  Math.signum(RIGHT_WEIGHTING.get(peasant.getID())) * 
					Math.pow(RIGHT_WEIGHTING.get(peasant.getID()), .02));
		}
		
		//If a peasant was uninjured, we use Bayes' Theorem to decrease the probability of a tower
		//near where the peasant was shot.  Also, we make our agent less cautious by reweighting
		//some of our heuristics.
		for (UnitView peasant : uninjured) {
			for (int x = peasant.getXPosition() - 4; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x >= 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y >= 0) {
							towerProbs[x][y] = ((1 - pShotGivenTower)*towerProbs[x][y])/(1 - getPShot(peasant, state));
						}
					}
				}
			}
			
			EXPLORATION_WEIGHTING.put(peasant.getID(), EXPLORATION_WEIGHTING.get(peasant.getID())*2.5);
			UP_WEIGHTING.put(peasant.getID(), UP_WEIGHTING.get(peasant.getID())*10.0);
			RIGHT_WEIGHTING.put(peasant.getID(), RIGHT_WEIGHTING.get(peasant.getID())*8.0);
		}
		
		/** Mark all Tower squares with max probability **/
		for (UnitView unit: state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("ScoutTower") ||
				unit.getTemplateView().getName().equalsIgnoreCase("GuardTower")) {
				knownTiles[unit.getXPosition()][unit.getYPosition()] = 1.0;
				occupied[unit.getXPosition()][unit.getYPosition()] = true;
			}
		}
		
		/** Mark all Resource squares with zero probability **/
		for (ResourceView resource: state.getAllResourceNodes()) {
			knownTiles[resource.getXPosition()][resource.getYPosition()] = .0;
			occupied[resource.getXPosition()][resource.getYPosition()] = true;
		}
		
		/** Mark all visibly empty tiles as having a zero probability that there is a tower **/
		for (UnitView peasant : getAllPeasants(state)) {
			for (int x = peasant.getXPosition() - 2; x < peasant.getXPosition() + 3; x++) {
				if (x >= 0 && x < state.getXExtent()) {
					for (int y = peasant.getYPosition() - 2; y < peasant.getYPosition() + 3; y++) {
						if (y >= 0 && y < state.getYExtent()) {
							if (!occupied[x][y]) {
								knownTiles[x][y] = 0.0;
							}
						}
					}
				}
			}
			
			visited[peasant.getXPosition()][peasant.getYPosition()] = true;
			occupied[peasant.getXPosition()][peasant.getYPosition()] = true;
		}
		
		//Find the next move
		for (UnitView peasant : getAllPeasants(state)) {
			Boolean deposit = false;
			Boolean gather = false;
			
			if (peasant.getCargoAmount() > 0) {
				for (UnitView townHall : state.getAllUnits()) {
					if (townHall.getTemplateView().getName().equalsIgnoreCase("TownHall")) {
						for (int x = peasant.getXPosition() - 1; x < peasant.getYPosition() + 1; x++) {
							for (int y = peasant.getXPosition() - 1; y < peasant.getYPosition() + 1; y++) {
								if (Math.abs(townHall.getXPosition() - x) < 2 &&Math.abs(townHall.getYPosition() - y) < 2) {
									toReturn.put(peasant.getID(), Action.createCompoundDeposit(peasant.getID(), townHall.getID()));
									deposit = true;
								}
							}
						}
					}
				}
			} else {
				for (ResourceView resource : state.getAllResourceNodes()) {
					if (resource.getType().toString().equalsIgnoreCase("GOLD_MINE")) {
						toReturn.put(peasant.getID(), Action.createCompoundGather(peasant.getID(), resource.getID()));
						UP_WEIGHTING.put(peasant.getID(), UP_WEIGHTING_INITIAL * -1);
						RIGHT_WEIGHTING.put(peasant.getID(), RIGHT_WEIGHTING_INITIAL * -1);
						gather = true;
					}
				}
			}
			
			/** If we aren't depositing or gathering, than find the best next move **/
			if (!deposit && !gather) {
				int xTile = 0;
				int yTile = 0;
				Double probability = Double.MAX_VALUE;
				
				for (int x = peasant.getXPosition() - 1; x <= peasant.getXPosition() + 1; x++) {
					if (x < state.getXExtent() && x >= 0) {
						for (int y = peasant.getYPosition() - 1; y <= peasant.getYPosition() + 1; y++) {
							if (y < state.getYExtent() && y >= 0 &&
									occupied[x][y] == false) {
								Double innerProbability = calculatureUtility(x, y, peasant, state);

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

				toReturn.put(peasant.getID(), Action.createCompoundMove(peasant.getID(), xTile, yTile));
			}
		}	

		return toReturn;
	}
	
	/**
	 * This utility function is a sum over the probability that there is a tower for each
	 * square in a 5 square radius around the peasant.  Additional heuristics are factored
	 * in for moving towards the gold mine or town hall as well as exploring new territory.
	 */
	private Double calculatureUtility(int x, int y, UnitView peasant, StateView state) {
		Double innerProbability = 0.0;
		
		for (int innerX = x - 4; innerX < x + 5; innerX++) {
			if (innerX < state.getXExtent() && innerX >= 0) {
				for (int innerY = y - 4; innerY < y + 5; innerY++) {
					if (innerY < state.getYExtent() && innerY >= 0) {
						if (knownTiles[innerX][innerY] != null &&
							knownTiles[innerX][innerY] == 1.0) {
							innerProbability += Double.MAX_VALUE;
						} else if (knownTiles[innerX][innerY] != null) {
							innerProbability += knownTiles[innerX][innerY];
						} else {
							innerProbability += towerProbs[innerX][innerY];
						}
					}
				}
			}
		}
		
		if (x > peasant.getXPosition()) {
			innerProbability /= RIGHT_WEIGHTING.get(peasant.getID());
		} 
		
		if (y < peasant.getYPosition()) {
			innerProbability /= UP_WEIGHTING.get(peasant.getID());
		}
		
		if (!visited[x][y]) {
			innerProbability /= EXPLORATION_WEIGHTING.get(peasant.getID());
		}
		
		return innerProbability;
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
	
	/** 
	 * Return the probability that a peasant in a given location will be shot given the
	 * data we currently have in our probability distribution.
	 */
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
