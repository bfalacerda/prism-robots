//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	* Hongyang Qu <hongyang.qu@cs.ox.ac.uk> (University of Oxford)
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
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

package prism;

import java.util.Vector;
import java.util.BitSet;


import acceptance.AcceptanceOmega;
import prism.DA;

/**
 * Class to store a deterministic automata and the corresponding transition labels
 */
public class DALabelsPair
{
	/** DA */
	private DA<BitSet, ? extends AcceptanceOmega> da;
	/** Labels */
	private Vector<BitSet> labelBS;


	/**
	 * Construct a pair.
	 */
	public DALabelsPair(DA<BitSet, ? extends AcceptanceOmega> da, Vector<BitSet> labelBS)
	{
		this.da = da;
		this.labelBS = labelBS;
	}

	public void setDA(DA<BitSet, ? extends AcceptanceOmega> da)
	{
		this.da = da;
	}

	public DA<BitSet, ? extends AcceptanceOmega> getDA() {
		return da;
	}

	public void setLabelBS(Vector<BitSet> labelBS)
	{
		this.labelBS = labelBS;
	}

	public Vector<BitSet> getLabelBS()
	{
		return labelBS;
	}

}
