package ontologizer.io.obo;

import static ontologizer.types.ByteString.b;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import ontologizer.io.ParserFileInput;
import ontologizer.ontology.RelationMeaning;
import ontologizer.ontology.Term;
import ontologizer.ontology.TermID;
import ontologizer.types.ByteString;

public class OBOParserTest
{
	@Rule
	public TestName testName = new TestName();

	/**
	 * Return the path to a file that contains the comment of the current test.
	 *
	 * @return an absolute path.
	 *
	 * @throws IOException
	 */
	private String getTestCommentAsPath() throws IOException
	{
		return TestSourceUtils.getCommentOfTestAsTmpFilePath(OBOParserTest.class,
			testName.getMethodName());
	}

	/**
	 * Return an obo parser suitable to parse the comment of the current test.
	 *
	 * @param options the options submitted to the parser.
	 *
	 * @return the obo parser
	 *
	 * @throws IOException
	 */
	private OBOParser getTestCommentAsOBOParser(int options) throws IOException
	{
		return new OBOParser(new ParserFileInput(getTestCommentAsPath()), options);
	}

	/**
	 * Returns an obo parser that has already parses the comment of the current
	 * test.
	 *
	 * @param options the obo parser options to customize the parser's behaviour.
	 * @return the obo parser
	 *
	 * @throws IOException
	 * @throws OBOParserException
	 */
	private OBOParser parseTestComment(int options) throws IOException, OBOParserException
	{
		OBOParser oboParser = getTestCommentAsOBOParser(options);
		oboParser.doParse();
		return oboParser;
	}

	/**
	 * Returns an obo parser that has already parsed the comment of the current
	 * test.
	 *
	 * @return the obo parser
	 *
	 * @throws IOException
	 * @throws OBOParserException
	 */
	private OBOParser parseTestComment() throws IOException, OBOParserException
	{
		return parseTestComment(0);
	}

	/* internal fields */
	public static final String GOtermsOBOFile = OBOParserTest.class.
			getClassLoader().getResource("gene_ontology.1_2.obo.gz").getPath();
	private static final int nTermCount = 35520;
	private static final int nRelations = 63105;
	private static final ByteString formatVersion = b("1.2");
	private static final ByteString date = b("04:01:2012 11:50");
	private static final ByteString data_version = b("1.1.2476");

	@Rule
	public TemporaryFolder tmpFolder = new TemporaryFolder();

	@Test
	public void testTermBasics() throws IOException, OBOParserException
	{
		/* Parse OBO file */
		System.out.println("Parse OBO file");
		OBOParser oboParser = new OBOParser(new ParserFileInput(GOtermsOBOFile));
		System.out.println(oboParser.doParse());
		HashMap<String,Term> id2Term = new HashMap<String,Term>();

		int relations = 0;
		for (Term t : oboParser.getTermMap())
		{
			relations += t.getParents().length;
			id2Term.put(t.getIDAsString(),t);
		}

		assertEquals(nTermCount, oboParser.getTermMap().size());
		assertEquals(formatVersion,oboParser.getFormatVersion());
		assertEquals(date,oboParser.getDate());
		assertEquals(data_version,oboParser.getDataVersion());
		assertEquals(nRelations,relations);
		assertTrue(id2Term.containsKey("GO:0008150"));
		assertEquals(0,id2Term.get("GO:0008150").getParents().length);
	}

	///
	/// [term]
	/// name: test
	/// id: GO:0000001
	///
	@Test
	public void testName() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		Set<Term> terms = oboParser.getTermMap();
		assertEquals(1, terms.size());
		assertEquals(b("test"), terms.iterator().next().getName());
	}

	@Test
	public void testIgnoreSynonyms() throws IOException, OBOParserException
	{
		OBOParser oboParser = new OBOParser(new ParserFileInput(GOtermsOBOFile),OBOParser.IGNORE_SYNONYMS);
		oboParser.doParse();
		for (Term t : oboParser.getTermMap())
			assertTrue(t.getSynonyms() == null || t.getSynonyms().length == 0);
	}

	/// [term]
	/// name: test\
	/// test\
	/// test
	/// id: GO:0000001
	@Test
	public void testMultiline() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		Set<Term> terms = oboParser.getTermMap();
		assertEquals(1, terms.size());
		assertEquals(b("testtesttest"), terms.iterator().next().getName());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	///
	/// [term]
	/// name: test2
	/// id: GO:0000002
	///
	/// relationship: part_of GO:0000001 ! test
	@Test
	public void testPartOf() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(2, terms.size());
		HashMap<String,Term> name2Term = new HashMap<String,Term>();
		for (Term t : terms)
			name2Term.put(t.getIDAsString(), t);
		assertEquals(RelationMeaning.PART_OF_A, name2Term.get("GO:0000002").getParents()[0].getRelation().meaning());
		assertEquals("GO:0000001", name2Term.get("GO:0000002").getParents()[0].getRelated().toString());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	///
	/// [term]
	/// name: test2
	/// id: GO:0000002
	///
	/// relationship: regulates GO:0000001 ! test
	@Test
	public void testRegulates() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		HashMap<String,Term> name2Term = new HashMap<String,Term>();
		for (Term t : terms)
			name2Term.put(t.getIDAsString(), t);
		assertEquals(RelationMeaning.REGULATES, name2Term.get("GO:0000002").getParents()[0].getRelation().meaning());
		assertEquals("GO:0000001", name2Term.get("GO:0000002").getParents()[0].getRelated().toString());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	///
	/// [term]
	/// name: test2
	/// id: GO:0000002
	/// relationship: zzz GO:0000001 ! test
	@Test
	public void testUnknownRelationship() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		HashMap<String,Term> name2Term = new HashMap<String,Term>();
		for (Term t : terms)
			name2Term.put(t.getIDAsString(), t);
		assertEquals(RelationMeaning.UNKOWN, name2Term.get("GO:0000002").getParents()[0].getRelation().meaning());
		assertEquals("zzz", name2Term.get("GO:0000002").getParents()[0].getRelation().name().toString());
		assertEquals("GO:0000001", name2Term.get("GO:0000002").getParents()[0].getRelated().toString());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// synonym: "test2"
	/// synonym: "test3" EXACT []
	@Test
	public void testSynonyms() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
		String [] expected = new String[]{"test2","test3"};
		assertEquals(expected.length, terms.get(0).getSynonyms().length);
		for (int i=0;i<expected.length;i++)
			assertEquals(expected[i],terms.get(0).getSynonyms()[i].toString());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// def: "This is a so-called \"test\""
	@Test
	public void testDef() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment(OBOParser.PARSE_DEFINITIONS);
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
		assertEquals("This is a so-called \"test\"", terms.get(0).getDefinition().toString());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	///
	/// [term]
	/// name: test2
	/// id: GO:0000002
	/// equivalent_to: GO:0000001
	/// equivalent_to: GO:0000003 ! comment
	@Test
	public void testEquivalent() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		HashMap<String,Term> name2Term = new HashMap<String,Term>();
		for (Term t : terms)
			name2Term.put(t.getIDAsString(), t);

		assertEquals(2,name2Term.get("GO:0000002").getEquivalents().length);
		HashSet<String> ids = new HashSet<String>();
		ids.add("GO:0000001");
		ids.add("GO:0000003");
		for (TermID id : name2Term.get("GO:0000002").getEquivalents())
			assertTrue(ids.contains(id.toString()));
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// is_obsolete: true
	@Test
	public void testObsolete() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertTrue(terms.get(0).isObsolete());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// xref: db:ID "WW"
	@Test
	public void testXRef() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment(OBOParser.PARSE_XREFS);
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
		assertEquals("db",terms.get(0).getXrefs()[0].getDatabase());
		assertEquals("ID",terms.get(0).getXrefs()[0].getXrefId());
		assertEquals("WW",terms.get(0).getXrefs()[0].getXrefName());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// xref: db:ID  "WW"
	@Test
	public void testXRef2Spaces() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment(OBOParser.PARSE_XREFS);
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
		assertEquals("db",terms.get(0).getXrefs()[0].getDatabase());
		assertEquals("ID",terms.get(0).getXrefs()[0].getXrefId());
		assertEquals("WW",terms.get(0).getXrefs()[0].getXrefName());
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// xref: db:ID
	@Test
	public void testSimpleXRef() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment(OBOParser.PARSE_XREFS);
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
		assertEquals("db",terms.get(0).getXrefs()[0].getDatabase());
		assertEquals("ID",terms.get(0).getXrefs()[0].getXrefId());
		assertNull(terms.get(0).getXrefs()[0].getXrefName());
	}

	/// subsetdef: subset \"Subset\"
	/// [term]
	/// name: test
	/// id: GO:0000001
	/// subset: subset
	/// [term]
	/// name: test2
	/// id: GO:0000002
	@Test
	public void testSubset() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(2, terms.size());
		assertEquals(1, terms.get(0).getSubsets().length);
		assertEquals("subset", terms.get(0).getSubsets()[0].getName().toString());
		assertEquals(0, terms.get(1).getSubsets().length);
	}

	/// [term]
	/// name: test
	/// id: GO:0000001
	/// alt_id: GO:0000003
	@Test
	public void testAltId() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
		assertEquals("GO:0000003", terms.get(0).getAlternatives()[0].toString());
	}

	/// [term
	/// import: sss
	@Test
	public void testExceptions() throws IOException
	{
		try
		{
			parseTestComment();
			assertTrue("Exception asserted", false);
		} catch (OBOParserException ex)
		{
			ex.printStackTrace();
			assertEquals(1,ex.linenum);
		}
	}

	@Test
	public void testExceptions2() throws IOException
	{
		/* We have to go without parseTestComment() due to an additional white-space after term */
		File tmp = tmpFolder.newFile();
		PrintWriter pw = new PrintWriter(tmp);
		pw.append("[term \nimport: sss\n");
		pw.close();

		OBOParser oboParser = new OBOParser(new ParserFileInput(tmp.getCanonicalPath()));
		try
		{
			oboParser.doParse();
			assertTrue("Exception asserted", false);
		} catch (OBOParserException ex)
		{
			ex.printStackTrace();
			assertEquals(1,ex.linenum);
		}
	}

	/// [term]
	/// name: test
	/// id: prefix:test
	@Test
	public void testArbitraryID() throws IOException, OBOParserException
	{
		OBOParser oboParser = parseTestComment();
		ArrayList<Term> terms = new ArrayList<Term>(oboParser.getTermMap());
		assertEquals(1, terms.size());
	}

}
