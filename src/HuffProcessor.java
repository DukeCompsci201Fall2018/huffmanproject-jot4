import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in); 
		
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHead(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		for (String chunk: codings) {
			out.writeBits(chunk.length(), Integer.parseInt(chunk, 2));
		}
	}

	/**
	 * Creates tree header (bit sequence representing the tree) from tree
	 * using a pre-order traversal (which requires recursion)
	 * @return the root of the tree
	 * @param root
	 * @param out
	 */
	private void writeHead(HuffNode root, BitOutputStream out) {
		if (root == null) {
			return;
		}
		
		if ((root.myLeft == null) && (root.myRight == null)) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
		
		writeHead(root.myLeft, out);
		writeHead(root.myRight, out);
		
//		int bit = in.readBits(1);
//		if (bit == -1) {
//			throw new HuffException("readBits method returns -1.");
//		}
//		if (bit == 0) {
//			HuffNode left = readTreeHeader(in);
//			HuffNode right = readTreeHeader(in);
//			return new HuffNode(0, 0, left, right);
//		} else {
//			int value = in.readBits(BITS_PER_WORD + 1);
//			return new HuffNode(value, 0, null, null);
//		}
		
	}
	
	/**
	 * Returns an array of Strings such that a[val]
	 * is the encoding of the 8-bit chunk val
	 * @param root
	 * @return array of encodings of 8-bit chunks
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}
	
	/**
	 * Recursive helper method for makeCodingsFromTree
	 * @param root
	 * @param path
	 * @param encodings
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root == null) {
			return;
		}
		
		if ((root.myLeft == null) && (root.myRight == null)) {
			encodings[root.myValue] = path;
			return;
		}
		
//		if (root.myLeft != null) {
//			codingHelper(root.myLeft)
//		}
		
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);		
	}

	/**
	 * Create a trie using a greedy algorithm
	 * and a priority queue of HuffNode objects
	 * @param counts
	 * @return root node of trie
	 */
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int i = 0; i < counts.length; i ++) {
			if (counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}
		
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			int parentWeight = left.myWeight + right.myWeight;
			HuffNode t = new HuffNode(0, parentWeight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
		
		return root;
	}

	/**
	 * Determine frequency of every eight-bit character/chunk
	 * in the file being compressed.
	 * @param in
	 * @return
	 */
	private int[] readForCounts(BitInputStream in) {

		int[] freq = new int[ALPH_SIZE + 1];
		
		freq[PSEUDO_EOF] = 1;
				
		while (true) {
			int charValue = in.readBits(BITS_PER_WORD);
			if (charValue == -1) {
				break;
			} else {
				freq[charValue] = freq[charValue] + 1;
			}
		}
		
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	/**
	 * Reads the bits from the BitInputStream representing the compressed file
	 * one bit at a time 
	 * @param root
	 * @param in
	 * @param out
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		// TODO Auto-generated method stub
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) {
					current = current.myLeft;
				} else {
					current = current.myRight;
				}
				
				if ((current.myLeft == null) && (current.myRight == null)) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					} else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
			}
			}
		}
		
	}
	
	/**
	 * Creates the tree from the tree header (bit sequence representing the tree)
	 * using a pre-order traversal (which requires recursion)
	 * @param in the tree header
	 * @return the root of the tree
	 */

	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("readBits method returns -1.");
		}
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		} else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
}