package org.apache.giraph.debugger.examples.exceptiondebug;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.BasicComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.utils.ArrayListWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Demonstrates triangle closing in simple, unweighted graphs for Giraph.
 * 
 * Triangle Closing: Vertex A and B maintain out-edges to C and D The algorithm,
 * when finished, populates all vertices' value with an array of Writables
 * representing all the vertices that each should form an out-edge to (connect
 * with, if this is a social graph.) In this example, vertices A and B would
 * hold empty arrays since they are already connected with C and D. Results: If
 * the graph is undirected, C would hold value, D and D would hold value C,
 * since both are neighbors of A and B and yet both were not previously
 * connected to each other.
 * 
 * In a social graph, the result values for vertex X would represent people that
 * are likely a part of a person X's social circle (they know one or more people
 * X is connected to already) but X had not previously met them yet. Given this
 * new information, X can decide to connect to vertices (peoople) in the result
 * array or not.
 * 
 * Results at each vertex are ordered in terms of the # of neighbors who are
 * connected to each vertex listed in the final vertex value. The more of a
 * vertex's neighbors who "know" someone, the stronger your social relationship
 * is presumed to be to that vertex (assuming a social graph) and the more
 * likely you should connect with them.
 * 
 * In this implementation, Edge Values are not used, but could be adapted to
 * represent additional qualities that could affect the ordering of the final
 * result array.
 */
public class BuggySimpleTriangleClosingComputation extends
  BasicComputation<IntWritable, IntWritable, NullWritable, IntWritable> {
  /** Vertices to close the triangle, ranked by frequency of in-msgs */
  private final Map<IntWritable, Integer> closeMap = Maps
    .<IntWritable, Integer> newHashMap();

  @Override
  public void compute(Vertex<IntWritable, IntWritable, NullWritable> vertex,
    Iterable<IntWritable> messages) throws IOException {
    if (getSuperstep() == 0) {
      // send list of this vertex's neighbors to all neighbors
      for (Edge<IntWritable, NullWritable> edge : vertex.getEdges()) {
        sendMessageToAllEdges(vertex, edge.getTargetVertexId());
      }
    } else {
      for (IntWritable message : messages) {
        // INTENTIONAL BUG: the original algorithm has these two lines, which
        // avoids the
        // NullPointerException, which the current code throws.
        // final int current = (closeMap.get(message) == null) ?
        // 0 : closeMap.get(message) + 1;
        final int current = closeMap.get(message);
        closeMap.put(message, current);
      }
      // make sure the result values are sorted and
      // packaged in an IntArrayListWritable for output
      Set<Pair> sortedResults = Sets.<Pair> newTreeSet();
      for (Map.Entry<IntWritable, Integer> entry : closeMap.entrySet()) {
        sortedResults.add(new Pair(entry.getKey(), entry.getValue()));
      }
      IntArrayListWritable outputList = new IntArrayListWritable();
      for (Pair pair : sortedResults) {
        if (pair.value > 0) {
          outputList.add(pair.key);
        } else {
          break;
        }
      }
      if (outputList.isEmpty()) {
        vertex.setValue(new IntWritable(-1));
      } else {
        vertex.setValue(outputList.get(0));
      }
    }
    vertex.voteToHalt();
  }

  /** Quick, immutable K,V storage for sorting in tree set */
  public static class Pair implements Comparable<Pair> {
    /**
     * key
     * 
     * @param key
     *          the IntWritable key
     */
    private final IntWritable key;
    /**
     * value
     * 
     * @param value
     *          the Integer value
     */
    private final Integer value;

    /**
     * Constructor
     * 
     * @param k
     *          the key
     * @param v
     *          the value
     */
    public Pair(IntWritable k, Integer v) {
      key = k;
      value = v;
    }

    /**
     * key getter
     * 
     * @return the key
     */
    public IntWritable getKey() {
      return key;
    }

    /**
     * value getter
     * 
     * @return the value
     */
    public Integer getValue() {
      return value;
    }

    /**
     * Comparator to quickly sort by values
     * 
     * @param other
     *          the Pair to compare with THIS
     * @return the comparison value as an integer
     */
    @Override
    public int compareTo(Pair other) {
      return other.value - this.value;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj instanceof Pair) {
        Pair other = (Pair) obj;
        return Objects.equal(value, other.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }
  }

  /**
   * Utility class for delivering the array of vertices THIS vertex should
   * connect with to close triangles with neighbors
   */
  @SuppressWarnings("serial")
  public static class IntArrayListWritable extends
    ArrayListWritable<IntWritable> {
    /** Default constructor for reflection */
    public IntArrayListWritable() {
      super();
    }

    /** Set storage type for this ArrayListWritable */
    @Override
    public void setClass() {
      setClass(IntWritable.class);
    }
  }
}
