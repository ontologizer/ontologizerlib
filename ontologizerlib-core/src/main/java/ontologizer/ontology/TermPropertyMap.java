package ontologizer.ontology;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

import sonumina.collections.IntMapper;
import sonumina.collections.Map;
import sonumina.collections.ObjectIntHashMap;

/**
 * Maps an arbitrary property to a given term. If multiple
 * keys map to different terms, only the first one is tracked.
 *
 * @param <K> the key type of the property.
 * @author Sebastian Bauer
 */
public class TermPropertyMap<K> implements Serializable
{
	private static final long serialVersionUID = 1L;

	private ObjectIntHashMap<K> keyMap;
	private TermMap termMap;

	private int ambiguities;

	private static final Collection<TermID> EMPTY_COLLECTION = Arrays.asList();

	/**
	 * Construct a term property map that maps arbitrary keys to terms.
	 *
	 * @param termMap the term map.
	 * @param map the map that specifies with keys should later map
	 *  to the given term.
	 */
	public TermPropertyMap(TermMap termMap, Map<Term, Collection<K>> map)
	{
		keyMap = new ObjectIntHashMap<K>();
		this.termMap = termMap;

		IntMapper<TermID> termIDMapper = termMap.getTermIDMapper();
		for (int i = 0; i < termIDMapper.getSize(); i++)
		{
			Term t = termMap.get(i);
			Collection<K> keys = map.map(t);

			for (K key : keys)
			{
				int newIndex = keyMap.getIfAbsentPut(key, i);
				if (i != newIndex)
				{
					/* Key was already present, count up ambiguities */
					ambiguities++;
				}
			}
		}
	}

	/**
	 * Return the term id associated with the given key.
	 *
	 * @param key the key to be sought
	 * @return term id that is associated to a term with the given key value
	 *  or null, if no such term exists.
	 */
	public TermID get(K key)
	{
		int index = keyMap.getIfAbsent(key, -1);
		if (index == -1)
		{
			return null;
		}
		return termMap.getTermIDMapper().get(index);
	}

	/**
	 * The index of the term id associated with the given key.
	 *
	 * @param key the key to be sought
	 * @return the index of the term id associated with the given key or -1 if the
	 *  key is not present.
	 */
	public int getIndex(K key)
	{
		return keyMap.getIfAbsent(key, -1);
	}

	/**
	 * @return the number of ambiguities.
	 */
	public int getAmbiguities()
	{
		return ambiguities;
	}

	/** Map term to alternative term ids */
	public static final Map<Term,Collection<TermID>> term2AltIdMap = new Map<Term,Collection<TermID>>()
	{
		@Override
		public Collection<TermID> map(Term key)
		{
			TermID [] alternatives = key.getAlternatives();
			if (alternatives == null || alternatives.length == 0)
			{
				return EMPTY_COLLECTION;
			}
			return Arrays.asList(alternatives);
		}
	};
}
