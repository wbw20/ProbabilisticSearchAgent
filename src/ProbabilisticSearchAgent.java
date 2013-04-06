import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;


public class ProbabilisticSearchAgent extends Agent {

	public ProbabilisticSearchAgent(int playernum) {
		super(playernum);
	}

	@Override
	public Map<Integer, Action> initialStep(StateView arg0, HistoryView arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void loadPlayerData(InputStream arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<Integer, Action> middleStep(StateView arg0, HistoryView arg1) {
		// TODO Auto-generated method stub
		return null;
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
