package ontologizer.ontology;

import static sonumina.math.graph.Algorithms.bfs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import sonumina.collections.Map;
import sonumina.math.graph.Algorithms;
import sonumina.math.graph.DirectedGraph;
import sonumina.math.graph.Edge;
import sonumina.math.graph.IDirectedGraph;
import sonumina.math.graph.IDistanceVisitor;
import sonumina.math.graph.INeighbourGrabber;
import sonumina.math.graph.IVisitor;
import sonumina.math.graph.SlimDirectedGraphView;

/**
 * Represents the whole ontology. Note that the terms "parents" and "children" are
 * used somewhat mixed here.
 *
 * @author Sebastian Bauer
 */
public class Ontology implements Iterable<Term>, IDirectedGraph<TermID>, Serializable
{
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(Ontology.class.getName());

	/** This is used to identify Gene Ontology until a better way is found */
	private static HashSet<String> level1TermNames = new HashSet<String>(Arrays.asList("molecular_function","biological_process", "cellular_component"));

	/** The graph */
	private DirectedGraph<TermID, RelationType> graph; /* FIXME: Edge type should a list of relations */

	/** We also pack a TermContainer */
	private TermContainer termContainer;

	/** The (possibly) artificial root term */
	private Term rootTerm;

	/** Level 1 terms */
	private List<TermID> level1terms = new ArrayList<TermID>();

	/** Available subsets */
	private HashSet <Subset> availableSubsets = new HashSet<Subset>();

	/**
	 * Terms often have alternative IDs (mostly from term merges). This map is used by
	 * getTermIncludingAlternatives(String termIdString) and initialized there lazily.
	 */
	private volatile TermPropertyMap<TermID> alternativeMap;

	/**
	 * Construct an Ontology graph from the given container.
	 *
	 * @param newTermContainer
	 * @deprecated use Ontology.create() instead
	 */
	@Deprecated
	public Ontology(TermContainer newTermContainer)
	{
		init(this, newTermContainer);
	}

	private Ontology() { }

	/**
	 * Returns the induced subgraph which contains the terms with the given ids.
	 *
	 * @param termIDs
	 * @return the subgraph induced by given the term ids.
	 */
	public Ontology getInducedGraph(Collection<TermID> termIDs)
	{
		Ontology subgraph 		= new Ontology();
		HashSet<TermID> allTerms 	= new HashSet<TermID>();

		for (TermID tid : termIDs)
			for (TermID tid2 : getTermsOfInducedGraph(null, tid))
				allTerms.add(tid2);

		subgraph.availableSubsets 	= availableSubsets;
		subgraph.graph 				= graph.subGraph(allTerms);
		subgraph.termContainer 		= termContainer;
		subgraph.availableSubsets 	= availableSubsets;

		subgraph.assignLevel1TermsAndFixRoot();

		return subgraph;
	}

	/**
	 * @return terms that have no descendants.
	 */
	public ArrayList<Term> getLeafTerms()
	{
		ArrayList<Term> leafTerms = new ArrayList<Term>();
		for (TermID t : graph.getVertices())
		{
			if (graph.getOutDegree(t) == 0)
				leafTerms.add(getTerm(t));
		}

		return leafTerms;
	}

	/**
	 * @return term id of terms that have no descendants.
	 */
	public Collection<TermID> getLeafTermIDs()
	{
		ArrayList<TermID> leafTerms = new ArrayList<TermID>();
		for (TermID t : graph.getVertices())
		{
			if (graph.getOutDegree(t) == 0)
				leafTerms.add(t);
		}

		return leafTerms;
	}

	/**
	 * @return the term ids in topological order.
	 */
	public List<TermID> getTermsInTopologicalOrder()
	{
		return Algorithms.topologicalOrder(graph);
	}

	/**
	 * @return a slim representation of the ontology.
	 */
	public SlimDirectedGraphView<Term> getSlimGraphView()
	{
		return SlimDirectedGraphView.create(graph, new Map<TermID,Term>()
		{
			@Override
			public Term map(TermID key)
			{
				return getTerm(key);
			}
		});
	}

	/**
	 * @return a slim representation with TermIDs as underlying type.
	 */
	public SlimDirectedGraphView<TermID> getTermIDSlimGraphView()
	{
		return SlimDirectedGraphView.create(graph);
	}

	/**
	 * Finds about level 1 terms and fix the root as we assume here
	 * that there is only a single root.
	 */
	private void assignLevel1TermsAndFixRoot()
	{
		level1terms = new ArrayList<TermID>();

		/* Find the terms without any ancestors */
		for (TermID tid : graph)
		{
			Term t;

			if (graph.getInDegree(tid) != 0)
				continue;

			t = termContainer.get(tid);
			if (t != null && t.isObsolete())
				continue;

			level1terms.add(tid);
		}

		if (level1terms.size() > 1)
		{
			StringBuilder level1StringBuilder = new StringBuilder();
			level1StringBuilder.append("\"");
			level1StringBuilder.append(getTerm(level1terms.get(0)).getName());
			level1StringBuilder.append("\"");
			for (int i=1;i<level1terms.size();i++)
			{
				level1StringBuilder.append(" ,\"");
				level1StringBuilder.append(getTerm(level1terms.get(i)).getName());
				level1StringBuilder.append("\"");
			}

			String rootName = "root";
			if (level1terms.size() == 3)
			{
				boolean isGO = false;
				for (TermID t : level1terms)
				{
					if (level1TermNames.contains(getTerm(t).getName().toString().toLowerCase())) isGO = true;
					else
					{
						isGO = false;
						break;
					}
				}
				if (isGO) rootName = "Gene Ontology";
			}

			rootTerm = new Term(level1terms.get(0).getPrefix().toString()+":0000000", rootName);

			logger.log(Level.INFO,"Ontology contains multiple level-one terms: " + level1StringBuilder.toString() + ". Adding artificial root term \"" + rootTerm.getID().toString() + "\".");

			rootTerm.setSubsets(new ArrayList<Subset>(availableSubsets));
			graph.addVertex(rootTerm.getID());

			for (TermID lvl1 : level1terms)
			{
				graph.addEdge(rootTerm.getID(), lvl1, RelationType.UNKNOWN);
			}
		} else if (rootTerm == null) /* Root term may be not null if a sub graph was created */
		{
			if (level1terms.size() == 1)
			{
				rootTerm = termContainer.get(level1terms.get(0));

				logger.log(Level.INFO,"Ontology contains a single level-one term ("+ rootTerm.toString() + "");
			}
		}
	}

	/**
	 * @return whether the given id is the id of the (possible artificial)
	 *  root term
	 */
	public boolean isRootTerm(TermID id)
	{
		return id.equals(rootTerm.getID());
	}

	/**
	 * Determines if the given term is the artificial root term.
	 *
	 * @param id the id of the term to check
	 * @return if id is the artificial root term.
	 */
	public boolean isArtificialRootTerm(TermID id)
	{
		return isRootTerm(id) && !level1terms.contains(id);
	}

	/**
	 * Get (possibly artificial) TermID of the root vertex of graph
	 *
	 * @return The term representing to root
	 */
	public Term getRootTerm()
	{
		return rootTerm;
	}

	/**
	 * @return all available subsets.
	 */
	public Collection<Subset> getAvailableSubsets()
	{
		return availableSubsets;
	}

	/**
	 * Return the set of term IDs containing the given term's children.
	 *
	 * @param termID - the term's id as a TermID
	 * @return the set of termID of the descendants as term-IDs
	 */
	public Set<TermID> getTermChildren(TermID termID)
	{
		Term goTerm;
		if (rootTerm.getID().id == termID.id)
			goTerm = rootTerm;
		else
			goTerm = termContainer.get(termID);

		HashSet<TermID> terms = new HashSet<TermID>();
		Iterator<Edge<TermID,RelationType>> edgeIter = graph.getOutEdges(goTerm.getID());
		while (edgeIter.hasNext())
			terms.add(edgeIter.next().getDest());
		return terms;
	}

	/**
	 * Return the set of terms containing the given term's children.
	 *
	 * @param term - the term for which the children should be returned
	 * @return the set of terms of the descendants as terms
	 */
	public Set<Term> getTermChildren(Term term)
	{
		Term goTerm;
		if (rootTerm.getID().id == term.getID().id)
			goTerm = rootTerm;
		else
			goTerm = termContainer.get(term.getID());

		HashSet<Term> terms = new HashSet<Term>();
		Iterator<Edge<TermID,RelationType>> edgeIter = graph.getOutEdges(goTerm.getID());
		while (edgeIter.hasNext())
			terms.add(getTerm(edgeIter.next().getDest()));
		return terms;
	}

	/**
	 * Return the set of term IDs containing the given term-ID's ancestors.
	 *
	 * @param goTermID
	 * @return the set of Term-IDs of ancestors
	 */
	public Set<TermID> getTermParents(TermID goTermID)
	{
		HashSet<TermID> terms = new HashSet<TermID>();
		if (rootTerm.getID().id == goTermID.id)
			return terms;

		Term goTerm;
		if (goTermID.equals(rootTerm.getIDAsString()))
			goTerm = rootTerm;
		else
			goTerm = termContainer.get(goTermID);

		Iterator<Edge<TermID,RelationType>> edgeIter = graph.getInEdges(goTerm.getID());
		while (edgeIter.hasNext())
			terms.add(edgeIter.next().getSource());
		return terms;
	}

	/**
	 * Return the set of terms that are parents of the given term.
	 *
	 * @param term
	 * @return the set of Terms of parents
	 */
	public Set<Term> getTermParents(Term term)
	{
		HashSet<Term> terms = new HashSet<Term>();
		if (rootTerm.getID().id == term.getID().id)
			return terms;

		Term goTerm;
		if (term.getID().equals(rootTerm.getIDAsString()))
			goTerm = rootTerm;
		else
			goTerm = termContainer.get(term.getID());

		Iterator<Edge<TermID,RelationType>> edgeIter = graph.getInEdges(goTerm.getID());
		while (edgeIter.hasNext())
			terms.add(getTerm(edgeIter.next().getSource()));
		return terms;
	}


	/**
	 * Return the set of GO term IDs containing the given GO term's parents.
	 *
	 * @param goTermID
	 * @return the set of parent terms including the type of relationship.
	 */
	public Set<ParentTermID> getTermParentsWithRelation(TermID goTermID)
	{
		HashSet<ParentTermID> terms = new HashSet<ParentTermID>();
		if (rootTerm.getID().id == goTermID.id)
			return terms;

		Term goTerm = termContainer.get(goTermID);

		Iterator<Edge<TermID,RelationType>> edgeIter = graph.getInEdges(goTerm.getID());
		while (edgeIter.hasNext())
		{
			Edge<TermID,RelationType> t = edgeIter.next();
			terms.add(new ParentTermID(t.getSource(), t.getData()));
		}

		return terms;
	}

	/**
	 * Get the relation that relates term to the parent or null.
	 *
	 * @param parent selects the parent
	 * @param term selects the term
	 * @return the relation type of term and the parent term or null if no
	 *  parent is not the parent of term.
	 */
	public RelationType getDirectRelation(TermID parent, TermID term)
	{
		Set<ParentTermID> parents = getTermParentsWithRelation(term);
		for (ParentTermID p : parents)
			if (p.getRelated().equals(parent)) return p.getRelation();
		return null;
	}

	/**
	 * Returns the siblings of the term, i.e., terms that are also children of the
	 * parents.
	 *
	 * @param tid
	 * @return set the siblings
	 */
	public Set<TermID> getTermsSiblings(TermID tid)
	{
		Set<TermID> parentTerms = getTermParents(tid);
		HashSet<TermID> siblings = new HashSet<TermID>();
		for (TermID p : parentTerms)
			siblings.addAll(getTermChildren(p));
		siblings.remove(tid);
		return siblings;
	}

	/**
	 * Determines if there exists a directed path from sourceID to destID on the
	 * ontology graph (in that direction).
	 *
	 * @param sourceID the id of the source term
	 * @param destID teh id of the destination term
	 */
	public boolean existsPath(final TermID sourceID, TermID destID)
	{
		/* Some special cases because of the artificial root */
		if (isRootTerm(destID))
		{
			if (isRootTerm(sourceID))
				return true;
			return false;
		}

		/*
		 * We walk from the destination to the source against the graph
		 * direction. Basically a breadth-depth search is done.
		 */

		final boolean [] pathExists = new boolean[1];

		graph.bfs(destID, true, new IVisitor<TermID>()
		{
			@Override
			public boolean visited(TermID vertex)
			{
				if (!vertex.equals(sourceID))
					return true;

				pathExists[0] = true;
				return false;
			}
		});

		return pathExists[0];
	}

	/**
	 * This interface is used as a callback mechanisim by the walkToSource()
	 * and walkToSinks() methods.
	 *
	 * @author Sebastian Bauer
	 */
	public interface ITermIDVisitor extends IVisitor<TermID>{};

	/**
	 * Starting at the vertex representing goTermID walk to the source of the
	 * DAG (ontology vertex) and call the method visiting of given object
	 * implementimg ITermIDVisitor.
	 *
	 * @param goTermID
	 *            the TermID to start with (note that visiting() is also called
	 *            for this vertex)
	 *
	 * @param vistingVertex
	 */
	public void walkToSource(TermID goTermID, ITermIDVisitor vistingVertex)
	{
		ArrayList<TermID> set = new ArrayList<TermID>(1);
		set.add(goTermID);
		walkToSource(set, vistingVertex);
	}

	/**
	 * Convert a collection of termids to a list of terms.
	 *
	 * @param termIDSet
	 * @return
	 */
	private ArrayList<Term> termIDsToTerms(Collection<TermID> termIDSet)
	{
		ArrayList<Term> termList = new ArrayList<Term>(termIDSet.size());
		for (TermID id : termIDSet)
		{
			Term t;

			if (isRootTerm(id)) t = rootTerm;
			else t = termContainer.get(id);
			if (t == null)
				throw new IllegalArgumentException("\"" + id + "\" could not be mapped to a known term!");

			termList.add(t);
		}
		return termList;
	}

	/**
	 * Starting at the vertices within the goTermIDSet walk to the source of the
	 * DAG (ontology vertex) and call the method visiting of given object
	 * Implementing ITermIDVisitor.
	 *
	 * @param termIDSet
	 *            the set of go TermsIDs to start with (note that visiting() is
	 *            also called for those vertices/terms)
	 *
	 * @param vistingVertex
	 */
	public void walkToSource(Collection<TermID> termIDSet, ITermIDVisitor vistingVertex)
	{
		graph.bfs(termIDSet, true, vistingVertex);
	}

	/**
	 * Starting at the vertices within the goTermIDSet walk to the source of the
	 * DAG (ontology vertex) and call the method visiting of given object
	 * Implementing ITermIDVisitor. Only relations in relationsToFollow are
	 * considered.
	 *
	 * @param termIDSet
	 * @param vistingVertex
	 * @param relationsToFollow
	 */
	public void walkToSource(Collection<TermID>  termIDSet, ITermIDVisitor vistingVertex, final Set<RelationMeaning> relationsToFollow)
	{
		bfs(termIDSet, new INeighbourGrabber<TermID>() {
			public Iterator<TermID> grabNeighbours(TermID t)
			{
				Iterator<Edge<TermID,RelationType>> inIter = graph.getInEdges(t);
				ArrayList<TermID> termsToConsider = new ArrayList<TermID>();
				while (inIter.hasNext())
				{
					Edge<TermID,RelationType> edge = inIter.next();
					if (relationsToFollow.contains(edge.getData().meaning()))
						termsToConsider.add(edge.getSource());
				}
				return termsToConsider.iterator();
			}
		}, vistingVertex);
	}

	/**
	 * Starting at the vertices within the goTermIDSet walk to the sinks of the
	 * DAG and call the method visiting of given object implementing
	 * ITermIDVisitor.
	 *
	 * @param goTermID
	 *            the TermID to start with (note that visiting() is also called
	 *            for this vertex)
	 *
	 * @param vistingVertex
	 */

	public void walkToSinks(TermID goTermID, ITermIDVisitor vistingVertex)
	{
		ArrayList<TermID> set = new ArrayList<TermID>(1);
		set.add(goTermID);
		walkToSinks(set, vistingVertex);
	}

	/**
	 * Starting at the vertices within the goTermIDSet walk to the sinks of the
	 * DAG and call the method visiting of given object implementing
	 * ITermIDVisitor.
	 *
	 * @param goTermIDSet
	 *            the set of go TermsIDs to start with (note that visiting() is
	 *            also called for those vertices/terms)
	 *
	 * @param vistingVertex
	 */
	public void walkToSinks(Collection<TermID> goTermIDSet, ITermIDVisitor vistingVertex)
	{
		graph.bfs(goTermIDSet, false, vistingVertex);
	}

	/**
	 * Returns the term container attached to this ontology graph.
	 * Note that the term container usually contains all terms while
	 * the graph object may contain a subset.
	 *
	 * @return the term container attached to this ontology graph.
	 * @deprecated Use getTermMap
	 */
	@Deprecated
	public TermMap getTermContainer()
	{
		return termContainer;
	}

	/**
	 * Return the term map attached to this ontology graph.
	 * Note that the term container usually contains all terms while
	 * the graph object may contain a subset.
	 *
	 * @return the term map
	 */
	public TermMap getTermMap()
	{
		return termContainer;
	}

	/**
	 * Returns the term represented by the given term id string or null.
	 *
	 * @param termId the term string id
	 * @return the proper term object corresponding to the given term string id.
	 */
	public Term getTerm(String termId)
	{
		Term go = termContainer.get(termId);
		if (go == null)
		{
			/* GO Term Container doesn't include the root term so we have to handle
			 * this case for our own.
			 */
			try
			{
				TermID id = new TermID(termId);
				if (id.id == rootTerm.getID().id)
					return rootTerm;
			} catch (IllegalArgumentException iea)
			{
			}
			return null;
		}
		/*
		 * In order to avoid the returning of terms that
		 * are only in the TermContainer but not in the graph
		 * we check here that the term is contained in the graph.
		 */
		if (  ! graph.containsVertex(go.getID()) ){
			return null;
		}

		return go;
	}

	/**
	 * A method to get a term using the term-ID as string.
	 * If no term with the given primary ID is found all
	 * alternative IDs are used. If still no term is found null is returned.
	 *
	 * @param termId the term id string
	 * @return the term.
	 */
	public Term getTermIncludingAlternatives(String termId)
	{
		Term term = getTerm(termId);
		if (term != null)
			return term;

		/* No term with this primary id could be found, try alternatives */
		setupAltIdMap();

		int index = alternativeMap.getIndex(new TermID(termId));
		if (index == -1)
		{
			return null;
		}
		return termContainer.get(index);
	}

	/**
	 * Setup the alternative id map if this hasn't been done before.
	 */
	private void setupAltIdMap()
	{
		if (alternativeMap == null)
		{
			synchronized (this)
			{
				if (alternativeMap == null)
				{
					alternativeMap = new TermPropertyMap<TermID>(termContainer, TermPropertyMap.term2AltIdMap);
				}
			}
		}
	}

	/**
	 * Returns the full-fledged term given its id.
	 *
	 * @param id the term id
	 * @return the term instance.
	 */
	public Term getTerm(TermID id)
	{
		Term go = termContainer.get(id);
		if (go == null && id.id == rootTerm.getID().id)
			return rootTerm;
		return go;
	}


	/**
	 * Returns whether the given term is included in the graph.
	 *
	 * @param term which term to check
	 * @return if term is included in the graph.
	 */
	public boolean termExists(TermID term)
	{
		return graph.getOutDegree(term) != -1;
	}


	/**
	 * Returns the set of terms given from the set of term ids.
	 *
	 * @param termIDs
	 * @return set of terms
	 *
	 * @deprecated use termSet
	 */
	public Set<Term> getSetOfTermsFromSetOfTermIds(Set<TermID> termIDs)
	{
		return termSet(termIDs);
	}

	/**
	 * Return a set of terms given an iterable instance of term id objects.
	 *
	 * @param termIDs
	 * @return set of terms
	 */
	public Set<Term> termSet(Iterable<TermID> termIDs)
	{
		HashSet<Term> termSet = new HashSet<Term>();
		for (TermID tid : termIDs)
			termSet.add(getTerm(tid));
		return termSet;
	}

	/**
	 * Return a list of term ids given an iterable instance of term objects.
	 * Mostly a work horse for the other termIDList() methods.
	 *
	 * @param termIDs a collection where to store the term ids.
	 * @param terms the terms whose ids shall be placed into termIdList
	 * @return a collection of term ids
	 */
	private static <A extends Collection<TermID>> A termIDs(A termIDs, Iterable<Term> terms)
	{
		for (Term t : terms)
			termIDs.add(t.getID());
		return termIDs;
	}

	/**
	 * Return a list of term ids given an iterable instance of term objects.
	 *
	 * @param terms the collection of term objects
	 * @return list of term ids
	 */
	public static List<TermID> termIDList(Iterable<Term> terms)
	{
		return termIDs(new ArrayList<TermID>(), terms);
	}

	/**
	 * Return a list of term ids given a collection of term objects.
	 *
	 * @param terms the collection of term objects
	 * @return list of term ids
	 */
	public static List<TermID> termIDList(Collection<Term> terms)
	{
		return termIDs(new ArrayList<TermID>(terms.size()), terms);
	}

	/**
	 * Return a set of term ids given a collection of term objects.
	 *
	 * @param terms iterable of terms
	 * @return set of term ids
	 */
	public static Set<TermID> termIDSet(Iterable<Term> terms)
	{
		return termIDs(new HashSet<TermID>(), terms);
	}

	/**
	 * Returns a set of induced terms that are the terms of the induced graph.
	 * Providing null as root-term-ID will induce all terms up to the root to be included.
	 *
	 * @param rootTermID the root term (all terms up to this are included). if you provide null all terms
	 * up to the original root term are included.
	 * @param termID the inducing term.
	 *
	 * @return set of term ids
	 */
	public Set<TermID> getTermsOfInducedGraph(final TermID rootTermID, TermID termID)
	{
		HashSet<TermID> nodeSet = new HashSet<TermID>();

		/**
		 * Visitor which simply add all nodes to the nodeSet.
		 *
		 * @author Sebastian Bauer
		 */
		class Visitor implements ITermIDVisitor
		{
			public Ontology graph;
			public HashSet<TermID> nodeSet;

			public boolean visited(TermID term)
			{
				if (rootTermID != null && !graph.isRootTerm(rootTermID))
				{
					/*
					 * Only add the term if there exists a path
					 * from the requested root term to the visited
					 * term.
					 *
					 * TODO: Instead of existsPath() implement
					 * walkToGoTerm() to speed up the whole stuff
					 */
					if (term.equals(rootTermID) || graph.existsPath(rootTermID, term))
						nodeSet.add(term);
				} else
					nodeSet.add(term);

				return true;
			}
		};

		Visitor visitor = new Visitor();
		visitor.nodeSet = nodeSet;
		visitor.graph = this;

		walkToSource(termID, visitor);

		return nodeSet;
	}

	/**
	 * @return all level 1 terms.
	 */
	public Collection<Term> getLevel1Terms()
	{
		return termIDsToTerms(level1terms);
	}

	/**
	 * Returns the parents shared by both t1 and t2.
	 *
	 * @param t1 term 1
	 * @param t2 term 2
	 * @return set of term ids that defines the terms shared by t1 and t2
	 */
	public Collection<TermID> getSharedParents(TermID t1, TermID t2)
	{
		final Set<TermID> p1 = getTermsOfInducedGraph(null,t1);

		final ArrayList<TermID> sharedParents = new ArrayList<TermID>();

		walkToSource(t2, new ITermIDVisitor()
		{
			public boolean visited(TermID t2)
			{
				if (p1.contains(t2))
					sharedParents.add(t2);
				return true;
			}
		});

		return sharedParents;
	}

	public static class TermLevels
	{
		private HashMap<Integer,HashSet<TermID>> level2terms = new HashMap<Integer,HashSet<TermID>>();
		private HashMap<TermID,Integer> terms2level = new HashMap<TermID,Integer>();

		private int maxLevel = -1;

		public void putLevel(TermID tid, int distance)
		{
			HashSet<TermID> levelTerms = level2terms.get(distance);
			if (levelTerms == null)
			{
				levelTerms = new HashSet<TermID>();
				level2terms.put(distance, levelTerms);
			}
			levelTerms.add(tid);
			terms2level.put(tid,distance);

			if (distance > maxLevel) maxLevel = distance;
		}

		/**
		 * Returns the level of the given term.
		 *
		 * @param tid
		 * @return the level or -1 if the term is not included.
		 */
		public int getTermLevel(TermID tid)
		{
			Integer level = terms2level.get(tid);
			if (level == null) return -1;
			return level;
		}

		public Set<TermID> getLevelTermSet(int level)
		{
			return level2terms.get(level);
		}

		public int getMaxLevel()
		{
			return maxLevel;
		}
	};


	/**
	 * Returns the levels of the given terms starting from the root. Considers
	 * only the relevant terms.
	 *
	 * @param termids
	 * @return levels of the terms as defined in the set.
	 */
	public TermLevels getTermLevels(final Set<TermID> termids)
	{
		DirectedGraph<TermID,RelationType> transGraph;
		Term transRoot;

		if ((getRelevantSubontology() != null && !isRootTerm(getRelevantSubontology())) || getRelevantSubset() != null)
		{
			Ontology ontologyTransGraph = getOntlogyOfRelevantTerms();
			transGraph = ontologyTransGraph.graph;
			transRoot = ontologyTransGraph.getRootTerm();
		} else
		{
			transGraph = graph;
			transRoot = rootTerm;
		}

		final TermLevels levels = new TermLevels();

		transGraph.singleSourceLongestPath(transRoot.getID(), new IDistanceVisitor<TermID>()
				{
					public boolean visit(TermID vertex, List<TermID> path,
							int distance)
					{
						if (termids.contains(vertex))
							levels.putLevel(vertex,distance);
						return true;
					}});
		return levels;
	}

	/**
	 * Returns the number of terms in this ontology
	 *
	 * @return the number of terms.
	 */
	public int getNumberOfTerms()
	{
		return graph.getNumberOfVertices();
	}

	/**
	 * @return the highest term id used in this ontology.
	 */
	public int maximumTermID()
	{
		int id=0;

		for (Term t : termContainer)
		{
			if (t.getID().id > id)
				id = t.getID().id;
		}

		return id;
	}

	/**
	 * Returns an iterator to iterate over all terms
	 */
	public Iterator<Term> iterator()
	{
		return new Iterator<Term>()
		{
			private Iterator<TermID> iter = graph.iterator();

			@Override
			public boolean hasNext()
			{
				return iter.hasNext();
			}

			@Override
			public Term next()
			{
				return getTerm(iter.next());
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	private Subset relevantSubset;
	private Term relevantSubontology;


	/**
	 * Sets the relevant subset.
	 *
	 * @param subsetName
	 */
	public void setRelevantSubset(String subsetName)
	{
		for (Subset s : availableSubsets)
		{
			if (s.getName().equals(subsetName))
			{
				relevantSubset = s;
				return;
			}
		}

		relevantSubset = null;
		throw new IllegalArgumentException("Subset \"" + subsetName + "\" couldn't be found!");
	}

	/**
	 * @return the current relevant subject.
	 */
	public Subset getRelevantSubset()
	{
		return relevantSubset;
	}

	/**
	 * Sets the relevant subontology.
	 *
	 * @param subontologyName
	 */
	public void setRelevantSubontology(String subontologyName)
	{
		/* FIXME: That's so slow */
		for (Term t : termContainer)
		{
			if (t.getName().equals(subontologyName))
			{
				relevantSubontology = t;
				return;
			}
		}
		throw new IllegalArgumentException("Subontology \"" + subontologyName + "\" couldn't be found!");
	}

	/**
	 * @return the relevant subontology.
	 */
	public TermID getRelevantSubontology()
	{
		if (relevantSubontology != null) return relevantSubontology.getID();
		return rootTerm.getID();
	}

	public boolean isRelevantTerm(Term term)
	{
		return isRelevantTerm(term,  relevantSubset, relevantSubontology);
	}

	/**
	 * Returns whether the given term is relevant (i.e., is contained in a relevant sub ontology and subset).
	 *
	 * @param term the term to check
	 * @return whether term is relevant.
	 */
	public boolean isRelevantTerm(Term term, Subset relevantSubset, Term relevantSubontology)
	{
		if (relevantSubset != null)
		{
			boolean found = false;
			for (Subset s : term.getSubsets())
			{
				if (s.equals(relevantSubset))
				{
					found = true;
					break;
				}
			}
			if (!found) return false;
		}

		if (relevantSubontology != null)
		{
			if (term.getID().id != relevantSubontology.getID().id)
				if (!(existsPath(relevantSubontology.getID(), term.getID())))
					return false;
		}

		return true;
	}

	/**
	 * Returns whether the given term is relevant (i.e., is contained in a relevant sub ontology and subset).
	 *
	 * @param termId defines the id of the term to check.
	 * @return whether the term specified by the term id is relevant
	 */
	public boolean isRelevantTermID(TermID termId)
	{
		Term t;
		if (isRootTerm(termId)) t = rootTerm;
		else t = termContainer.get(termId);

		return isRelevantTerm(t);
	}

	/**
	 * From a given list of terms return those that are relevant.
	 *
	 * @param list
	 * @return list of relevant terms.
	 */
	public List<TermID> filterRelevant(List<TermID> list)
	{
		List<TermID> relevantList = new ArrayList<TermID>();
		for (TermID id : list)
		{
			if (isRelevantTermID(id))
				relevantList.add(id);
		}
		return relevantList;
	}

	/**
	 * Returns a redundant relation to this term.
	 *
	 * @param t term to check
	 * @return null, if there is no redundant relation
	 */
	public TermID findARedundantISARelation(Term t)
	{
		/* We implement a naive algorithm which results straight-forward from
		 * the definition: A relation is redundant if it can be removed without
		 * having a effect on the reachability of the nodes.
		 */
		Set<TermID> parents = getTermParents(t.getID());

		Set<TermID> allInducedTerms = getTermsOfInducedGraph(null,t.getID());

		for (TermID p : parents)
		{
			HashSet<TermID> thisInduced = new HashSet<TermID>();

			for (TermID p2 : parents)
			{
				/* Leave out the current parent */
				if (p.equals(p2)) continue;

				thisInduced.addAll(getTermsOfInducedGraph(null, p2));
			}

			if (thisInduced.size() == allInducedTerms.size() - 1)
				return p;

		}

		return null;
	}

	/**
	 * Finds redundant is a relations and outputs them.
	 */
	public void findRedundantISARelations()
	{
		for (Term t : this)
		{
			TermID redundant = findARedundantISARelation(t);
			if (redundant != null)
			{
				logger.log(Level.INFO, "{} ({}) -> {} ({})",  new Object[] {
						t.getName(), t.getIDAsString(), getTerm(redundant).getName(),
						redundant.toString() });
			}
		}
	}

	/**
	 * @return the graph of relevant terms.
	 */
	public Ontology getOntlogyOfRelevantTerms()
	{
		return getOntologyOfRelevantTerms(relevantSubset, relevantSubontology);
	}

	/**
	 * @return the graph of relevant terms.
	 */
	public Ontology getOntologyOfRelevantTerms(Subset relevantSubset, Term relevantSubontology)
	{
		HashSet<TermID> terms = new HashSet<TermID>();
		for (Term t : this)
		{
			if (isRelevantTerm(t, relevantSubset, relevantSubontology))
			{
				terms.add(t.getID());
			}
		}

		DirectedGraph<TermID,RelationType> trans = graph.pathMaintainingSubGraph(terms);

		Ontology g 		= new Ontology();
		g.graph 			= trans;
		g.termContainer	= termContainer;
		if (trans.containsVertex(rootTerm.getID()))
		{
			g.rootTerm = rootTerm;
		}
		g.assignLevel1TermsAndFixRoot();

		/* TODO: Fix edges */

		return g;
	}

	/**
	 * @return the underlying graph.
	 */
	public DirectedGraph<TermID,RelationType> getGraph()
	{
		/* We should think about removing this though */
		return graph;
	}

	/**
	 * Merges equivalent terms. The first term given to this
	 * method will be the representative of this
	 * "equivalence-cluster".
	 * @param t1
	 * @param eqTerms
	 */
	public void mergeTerms(Term t1, Iterable<Term> eqTerms)
	{
		HashSet<TermID> t1ExistingAlternatives = new HashSet<TermID>(Arrays.asList(t1.getAlternatives()));
		for (Term t : eqTerms)
		{
			TermID tId = t.getID();

			if (t1ExistingAlternatives.contains(tId))
				continue;

			t1.addAlternativeId(tId);
		}

		this.graph.mergeVertices(t1.getID(), termIDList(eqTerms));
	}

	@Override
	public Iterable<TermID> getParentNodes(TermID v)
	{
		return graph.getParentNodes(v);
	}

	@Override
	public Iterable<TermID> getChildNodes(TermID v)
	{
		return graph.getChildNodes(v);
	}

	/**
	 * Init the ontology from a term container.
	 *
	 * @param o the ontology to be initialized
	 * @param tc the term container from which to init the ontology.
	 */
	private static void init(Ontology o, TermContainer tc)
	{
		o.termContainer = tc;
		o.graph = new DirectedGraph<TermID,RelationType>();

		/* At first add all goterms to the graph */
		for (Term term : tc)
			o.graph.addVertex(term.getID());

		int skippedEdges = 0;

		/* Now add the edges, i.e. link the terms */
		for (Term term : tc)
		{
			if (term.getSubsets() != null)
				for (Subset s : term.getSubsets())
					o.availableSubsets.add(s);

			for (ParentTermID parent : term.getParents())
			{
				TermID related = parent.getRelated();
				/* Ignore loops */
				if (term.getID().equals(related))
				{
					logger.log(Level.INFO,"Detected self-loop in the definition of the ontology (term "+ term.getIDAsString()+"). This link has been ignored.");
					continue;
				}
				if (tc.get(related) == null)
				{
					/* FIXME: We may want to add a new vertex to graph here instead */
					logger.log(Level.INFO,"Could not add a link from term " + term.toString() + " to " + parent.getRelated().toString() +" as the latter's definition is missing.");
					++skippedEdges;
					continue;
				}
				o.graph.addEdge(parent.getRelated(), term.getID(), parent.getRelation());
			}
		}

		if (skippedEdges > 0)
			logger.log(Level.INFO,"A total of " + skippedEdges + " edges were skipped.");
		o.assignLevel1TermsAndFixRoot();

	}

	/**
	 * Create an ontology from a term container.
	 *
	 * @param tc defines the term container
	 * @return the ontology derived from tc
	 */
	public static Ontology create(TermContainer tc)
	{
		Ontology o = new Ontology();
		init(o, tc);
		return o;
	}
}
