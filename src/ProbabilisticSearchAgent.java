import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;


public class ProbabilisticSearchAgent extends Agent {
	//Approximation of the chances a random square has a tower
	private Double pTower = .00616;
	private Double pShotGivenTower = .75;
	private Double[][] towerProbs;
	private Map<Integer, Integer> peasants;

	public ProbabilisticSearchAgent(int playernum) {
		super(playernum);
	}

	@Override
	public Map<Integer, Action> initialStep(StateView state, HistoryView history) {
		towerProbs = new Double[state.getXExtent()][state.getYExtent()];
		peasants = new HashMap<Integer, Integer>();

		for (UnitView unit : state.getAllUnits()) {
			if (unit.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				peasants.put(unit.getID(), unit.getHP());
			}
		}
		
		for (int x = 0; x < towerProbs.length; x++) {
			for (int y = 0; y < towerProbs[x].length; y++) {
				towerProbs[x][y] = pTower;
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
				if (x < state.getXExtent() && x > 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y > 0) {
							towerProbs[x][y] *= (pShotGivenTower*pTower)/getPShot(peasant, state);
						}
					}
				}
			}
		}
		
		for (UnitView peasant : uninjured) {
			for (int x = peasant.getXPosition() - 5; x < peasant.getXPosition() + 5; x++) {
				if (x < state.getXExtent() && x > 0) {
					for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
						if (y < state.getYExtent() && y > 0) {
							towerProbs[x][y] *= ((1 - pShotGivenTower)*pTower)/(1 - getPShot(peasant, state));
						}
					}
				}
			}
		}
		
		for (UnitView peasant : state.getAllUnits()) {
			if (peasant.getTemplateView().getName().equalsIgnoreCase("Peasant")) {
				int xTile = 0;
				int yTile = 0;
				Double probability = Double.MAX_VALUE;
				
				for (int x = peasant.getXPosition() - 5; x < peasant.getXPosition() + 5; x++) {
					if (x < state.getXExtent() && x > 0) {
						for (int y = peasant.getYPosition() - 5; y < peasant.getYPosition() + 5; y++) {
							if (y < state.getYExtent() && y > 0) {
								if (x > peasant.getXPosition() || y < peasant.getYPosition()) {
									if (probability > towerProbs[x][y]) {
										xTile = x;
										yTile = y;
										probability = towerProbs[x][y];
									}
								}
							}
						}
					}
				}
				
				toReturn.put(peasant.getID(), Action.createCompoundMove(peasant.getID(), xTile, yTile));
			}
		}			
		
		return toReturn;
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
