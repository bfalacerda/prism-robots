//==============================================================================
//	
//	Copyright (c) 2013-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//  * Frits Dannenberg <frits.dannenberg@cs.ox.ac.uk> (University of Oxford)
//	* Ernst Moritz Hahn <emhahn@cs.ox.ac.uk> (University of Oxford)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package explicit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;

import parser.ast.Expression;
import parser.ast.ExpressionIdent;
import parser.ast.LabelList;
import parser.ast.RewardStruct;
import parser.type.TypeDouble;
import parser.Values;
import parser.State;
import prism.*;
import prism.Model;

//TODO REMOVE UNEEDED IMPORTS


/**
 * Implementation of the upper confidence bounds applied to trees (UCT) algorithm
 */
public final class UCT extends PrismComponent
{
	/**
	 * Stores a UCT search node.
	 */
	private final static class UCTNode
	{
		/** MDP state representation */
		private State state;
		/** The action that was executed to reach this node */
		private int action;
		/** The probability of reaching this node */
		private double reachProb;
		/** number of rollouts that have visited this node */
		private int numVisits;
		/** Have the succs of this node been computed*/
		private boolean expanded;
		/** expected reward estimate from this node, based on the previous rollouts */
		private double expectedRewEstimate;
		/** successor UTC nodes */
		private UCTNode[] succNodes;

		
		/**
		 * Constructs an UCT search node object.
		 * 
		 * @param state The MDP state representation
		 * @param action The action that brought the UCT search to this node.
		 */
		UCTNode(State state, int action, double reachProb)
		{
			this.state = state;
			this.action = action;
			this.reachProb = reachProb;
			this.numVisits = 0;
			this.expanded = false;
			this.expectedRewEstimate = 0.0;
			this.succNodes = null;
		}
		
		/**
		 * Gets the MDP state
		 * 
		 * @return MDP state
		 */
		State getState()
		{
			return state;
		}
		
		/**
		 * Gets the action that was executed to reach this UCT node
		 * 
		 * @return action
		 */
		int getAction()
		{
			return action;
		}
		
		/**
		 * Gets the probability to reach this UCT node from its parent
		 * 
		 * @return action
		 */
		double getReachProb()
		{
			return reachProb;
		}
		
		
		/**
		 * Gets the number of times this node has been visited
		 * 
		 * @return MDP state
		 */
		int getNumVisits()
		{
			return numVisits;
		}
		
		/**
		 * Gets the  estimate for the expected reward for this node, based on previous rollouts
		 * 
		 * @return MDP state
		 */
		double getExpectedRewEstimate()
		{
			return expectedRewEstimate;
		}
		
		/**
		 * Gets the children of this UTC node
		 * 
		 * @return MDP state
		 */
		UCTNode[] getSuccNodes()
		{
			return succNodes;
		}
	

		/**
		 * Increment number of visits to this node
		 * 
		 */
		void incrementNumVisits()
		{
			this.numVisits = this.numVisits + 1;
		}
		
		/**
		 * Update reward
		 * 
		 * @param reward state reward to set
		 */
		void updateExpectedRewEstimate(double reward)
		{
			this.expectedRewEstimate = ((this.numVisits - 1)*this.expectedRewEstimate + reward)/(this.numVisits);
		}
		

		/**
		 * Sets successor UCT nodes (i.e., children of this node).
		 * 
		 * @param succNodes successor nodes
		 */
		void setSuccNodes(UCTNode[] succNodes)
		{
			this.succNodes = succNodes;
		}

		/**
		 * Returns number of successor states of this state.
		 * 
		 * @return number of successor states
		 */
		int getNumSuccs()
		{
			if (succNodes == null) {
				return 0;
			} else {
				return succNodes.length;
			}
		}
		
		/**
		 * Checks whether this state has successors or not.
		 * Will be true if and only if successor state array is nonnull.
		 * 
		 * @return whether this state has successors or not
		 */
		boolean hasSuccs()
		{
			return succNodes != null;
		}

		
		/**
		 * Checks whether this node has been visited
		 * 
		 * @return whether this node has been visited
		 */
		boolean isExpanded()
		{
			return expanded;
		}
		
		void setAsExpanded()
		{
			expanded = true;
		}
		
		
		boolean isDecisionNode()
		{
			return action == -1;
		}
		
		double getUCTScore(double parentVisits, double bias)
		{
			if (numVisits == 0) {
				return Double.MAX_VALUE;
			} else {
				return bias*Math.sqrt(parentVisits/numVisits) + expectedRewEstimate;
			}
		}
	}
		



	/** model exploration component to generate new states */
	private ModelGenerator modelGen;
	/** rollout depth */
	private int depth;
	/** bias for UCT formula calculation */
	private double bias;
	
	/** reward structure to use for analysis */
	private RewardStruct rewStruct = null;
	/** model constants */
	private Values constantValues = null;

	///** maps from state (assignment of variable values) to property object */
	//private LinkedHashMap<State,StateProp> states;

	///** target state set - used for reachability (until or finally properties) */
	//private Expression target;
//	/** set of initial states of the model */
//	private HashSet<State> initStates;
	
	/**
	 * Constructor.
	 */
	public UCT(PrismComponent parent, ModelGenerator modelGen, double bias, int depth) throws PrismException
	{
		super(parent);
		
		this.modelGen = modelGen;
		this.bias = bias;
		this.depth = depth;
		rewStruct = null;
		constantValues = null;
	}


	/**
	 * Sets reward structure to use.
	 * 
	 * @param rewStruct reward structure to use
	 */
	public void setRewardStruct(RewardStruct rewStruct)
	{
		this.rewStruct = rewStruct;
	}
	
	/**
	 * Sets values for model constants.
	 * 
	 * @param constantValues values for model constants
	 */
	public void setConstantValues(Values constantValues)
	{
		this.constantValues = constantValues;
	}
	

	
	public void expandNode(UCTNode node) throws PrismException {
		int i, nc, nt;
		double prob;
		UCTNode[] succNodes;
		
		if (node.isDecisionNode()) {
			nc = modelGen.getNumChoices();
			succNodes = new UCTNode[nc];
			for (i = 0; i < nc; i++) {
				succNodes[i] = new UCTNode(node.state, i,-1);
			}
		}
		else {
			nt = modelGen.getNumTransitions(node.getAction());
			succNodes = new UCTNode[nt];
			for (i = 0; i < nt; i++) {
				prob = modelGen.getTransitionProbability(node.getAction(), i);
				State succState = modelGen.computeTransitionTarget(node.getAction(), i);
				succNodes[i] = new UCTNode(succState, -1, prob);
				
			}
		}
		node.setSuccNodes(succNodes);
		node.setAsExpanded();
	}
	
	public UCTNode getBestUCTSucc(UCTNode node) {
		int i, numSuccs;
		double score, maxScore = Double.MIN_VALUE;
		UCTNode succNode, bestSucc;
		UCTNode[] succNodes = node.getSuccNodes();
		
		bestSucc = null;
		numSuccs = node.getNumSuccs();
		for (i = 0; i < numSuccs; i++) {
			succNode = succNodes[i];
			score = succNode.getUCTScore(node.getNumVisits(), bias);
			if (score == Double.MAX_VALUE) {
				return succNode;
			}
			if (score > maxScore) {
				bestSucc = succNode;
			}
		}
		//System.out.println("PILA" + bestSucc);
		return bestSucc;
	}
	
	public double getReward(State state, String action) throws PrismException{
		System.out.println("ACTION " + action);
		int numStateItems = rewStruct.getNumItems();
		double res = 0.0;
		for (int i = 0; i < numStateItems; i++) {
			Expression guard = rewStruct.getStates(i);
			if (guard.evaluateBoolean(constantValues, state)) {
				String currentAction = rewStruct.getSynch(i);
				if (action.equals(currentAction)) {
					double reward = rewStruct.getReward(i).evaluateDouble(constantValues, state);
					System.out.print("ACTION " + action + "reward=" + reward);
					res = res + rewStruct.getReward(i).evaluateDouble(constantValues, state);
				}
			}
		}
		return res;
	}
	
	public UCTNode sampleSucc(UCTNode node) {
		int i, numSuccs;
		double sampled, currentProbSum = 0.0;
		UCTNode currentSucc = null;
		UCTNode[] succs;
		
		sampled = Math.random();
		numSuccs = node.getNumSuccs();
		succs = node.getSuccNodes();
		for (i = 0; i < numSuccs; i++) {
			currentSucc = succs[i];
			currentProbSum = currentProbSum + currentSucc.getReachProb();
			if (currentProbSum >= sampled) {
				return currentSucc;
				//break;
			}
		}
		System.out.println("FODA_SE");
		return currentSucc;
	}
	
	public double rollout(UCTNode node, int depth) throws PrismException {
		double res = 0.0;
		UCTNode succNode;
		
		if (depth == 0) {
			return 0;
		}
		modelGen.exploreState(node.getState());
		if (!node.isExpanded()) {
			expandNode(node);
		}

		node.incrementNumVisits();
		if (node.isDecisionNode()) {
			succNode = getBestUCTSucc(node);
			if (succNode == null) {
				depth = 0;
			} else {
				modelGen.exploreState(succNode.getState());
				String actionString =  modelGen.getTransitionAction(succNode.getAction()).toString();
				modelGen.exploreState(node.getState());
				res = getReward(node.getState(), actionString);
			}
		} else {
			succNode = sampleSucc(node);
			depth = depth - 1;
			
		}
		res = res + rollout(succNode, depth);
		node.updateExpectedRewEstimate(res);
	
		return res;

	}
	
	public void search() throws PrismException
	{
		if (!modelGen.hasSingleInitialState())
			throw new PrismException("UCT rquires a single initial state");
		
		mainLog.println("\nRunning UCT...");
		State initState = modelGen.getInitialState();
		UCTNode initNode = new UCTNode(initState, -1, 1);
		for (int i = 0; i < 10; i++) {
			rollout(initNode, this.depth);
		}
		System.out.println("PILA" + initNode.getExpectedRewEstimate());
		getBestPolicy(initNode);
	}
	
	
	public void getBestPolicy(UCTNode node) throws PrismException{
		if (node.getAction() != -1) {
			modelGen.exploreState(node.state);
			System.out.println(modelGen.getTransitionAction(node.getAction()).toString());
		}
		System.out.println("HELLO");
		double max = Double.MIN_VALUE;
		UCTNode bestNextNode = null;
		for (int i = 0; i < node.getNumSuccs(); i++) {
			UCTNode currentNode = node.getSuccNodes()[i];
			if (currentNode.getExpectedRewEstimate() > max) {
				bestNextNode = currentNode;
			}
		}
		if (bestNextNode != null) {
			getBestPolicy(bestNextNode);
		}
	}
	
	
}
	

//	/**
//	 * 
//	 * @param target
//	 */
//	public void setTarget(Expression target)
//	{
//		this.target = target;
//	}
	


/*
	*//**
	 * Compute transient probability distribution (forwards).
	 * Start from initial state (or uniform distribution over multiple initial states).
	 *//*
	public StateValues doTransient(double time) throws PrismException
	{
		return doTransient(time, (StateValues) null);
	}

	*//**
	 * Compute transient probability distribution (forwards).
	 * Optionally, use the passed in file initDistFile to give the initial probability distribution (time 0).
	 * If null, start from initial state (or uniform distribution over multiple initial states).
	 * @param t Time point
	 * @param initDistFile File containing initial distribution
	 * @param currentModel 
	 *//*
	public StateValues doTransient(double t, File initDistFile, Model model)
			throws PrismException
	{
		StateValues initDist = null;
		if (initDistFile != null) {
			int numValues = countNumStates(initDistFile);
			initDist = new StateValues(TypeDouble.getInstance(), numValues);
			initDist.readFromFile(initDistFile);
		}
		return doTransient(t, initDist);
	}


	*//**
	 * Compute transient probability distribution (forwards).
	 * Use the passed in vector initDist as the initial probability distribution (time 0).
	 * In case initDist is null starts at the default initial state with prob 1.
	 * 
	 * @param time Time point
	 * @param initDist Initial distribution
	 *//*
	public StateValues doTransient(double time, StateValues initDist) throws PrismException
	{
		if (!modelGen.hasSingleInitialState())
			throw new PrismException("Fast adaptive uniformisation does not yet support models with multiple initial states");
		
		mainLog.println("\nComputing probabilities (fast adaptive uniformisation)...");
		
		if (initDist == null) {
			initDist = new StateValues();
			initDist.type = TypeDouble.getInstance();
			initDist.size = 1;
			initDist.valuesD = new double[1];
			initDist.statesList = new ArrayList<State>();
			initDist.valuesD[0] = 1.0;
			initDist.statesList.add(modelGen.getInitialState());
		}

	*//**
	 * Computes successor rates and rewards for a given state.
	 * Rewards computed depend on the reward structure set by
	 * {@code setRewardStruct}.
	 * 
	 * @param state state to compute successor rates and rewards for
	 * @throws PrismException thrown if something goes wrong
	 *//*
	private void computeStateRatesAndRewards(State state) throws PrismException
	{
		double[] succRates;
		StateProp[] succStates;
		modelGen.exploreState(state);
		specialLabels.setLabel(0, modelGen.getNumTransitions() == 0 ? Expression.True() : Expression.False());
		specialLabels.setLabel(1, initStates.contains(state) ? Expression.True() : Expression.False());
		Expression evSink = sink.deepCopy();
		evSink = (Expression) evSink.expandLabels(specialLabels);
		if (evSink.evaluateBoolean(constantValues, state)) {
			succRates = new double[1];
			succStates = new StateProp[1];
			succRates[0] = 1.0;
			succStates[0] = states.get(state);
		} else {
			int nc = modelGen.getNumChoices();
			succRates = new double[nc];
			succStates = new StateProp[nc];
			for (int i = 0; i < nc; i++) {
				int nt = modelGen.getNumTransitions(i);
				for (int j = 0; j < nt; j++) {
					State succState = modelGen.computeTransitionTarget(i, j);
					StateProp succProp = states.get(succState);
					if (null == succProp) {
						addToModel(succState);
						modelGen.exploreState(state);
						succProp = states.get(succState);
					}
					succRates[i] = modelGen.getTransitionProbability(i, j);
					succStates[i] = succProp;
				}
			}
			if (nc == 0) {
				succRates = new double[1];
				succStates = new StateProp[1];
				succRates[0] = 1.0;
				succStates[0] = states.get(state);
			}
		}
		states.get(state).setSuccRates(succRates);
		states.get(state).setSuccStates(succStates);
	}




*/