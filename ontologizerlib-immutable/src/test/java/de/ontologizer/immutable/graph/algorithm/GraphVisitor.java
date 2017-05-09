package de.ontologizer.immutable.graph.algorithm;

import de.ontologizer.immutable.graph.DirectedGraph;
import de.ontologizer.immutable.graph.algorithms.VertexVisitor;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for tests requiring a {@link VertexVisitor}.
 *
 * @author <a href="mailto:manuel.holtgrewe@bihealth.de">Manuel Holtgrewe</a>
 */
class GraphVisitor implements VertexVisitor<Integer> {

	private final List<Integer> visitedVertices = new ArrayList<Integer>();

	@Override
	public boolean visit(DirectedGraph<Integer> g, Integer v) {
		visitedVertices.add(v);
		return true;
	}

	public List<Integer> getVisitedVertices() {
		return visitedVertices;
	}

}