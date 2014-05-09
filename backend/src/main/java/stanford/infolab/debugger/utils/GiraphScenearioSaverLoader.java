package stanford.infolab.debugger.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.giraph.graph.Computation;
import org.apache.giraph.utils.ReflectionUtils;
import org.apache.giraph.utils.WritableUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

import stanford.infolab.debugger.Scenario.GiraphScenario;
import stanford.infolab.debugger.Scenario.GiraphScenario.Context;
import stanford.infolab.debugger.Scenario.GiraphScenario.Context.Neighbor;
import stanford.infolab.debugger.Scenario.GiraphScenario.Context.OutMsg;
import stanford.infolab.debugger.Scenario.GiraphScenario.ContextOrBuilder;
import stanford.infolab.debugger.utils.GiraphScenarioWrapper.ContextWrapper.OutgoingMessageWrapper;

import com.google.protobuf.ByteString;

/**
 * An object to load and save Protocol Buffer file.
 *
 * @param <I>   Vertex ID
 * @param <V>   Vertex value
 * @param <E>   Edge value
 * @param <M1>  Incoming message
 * @param <M2>  Outgoing message
 * 
 * @author Brian Truong
 */
@SuppressWarnings("rawtypes")
public class GiraphScenearioSaverLoader<I extends WritableComparable, V extends Writable, 
    E extends Writable, M1 extends Writable, M2 extends Writable> {

  public GiraphScenarioWrapper<I, V, E, M1, M2> load(String fileName)
      throws ClassNotFoundException, IOException {
    GiraphScenario giraphScenario = GiraphScenario.parseFrom(new FileInputStream(fileName));
    return loadGiraphScneario(giraphScenario);
  }

  public GiraphScenarioWrapper<I, V, E, M1, M2> loadFromHDFS(FileSystem fs, String fileName)
    throws ClassNotFoundException, IOException {
    GiraphScenario giraphScenario = GiraphScenario.parseFrom(fs.open(new Path(fileName)));
    return loadGiraphScneario(giraphScenario);
  }
  
  @SuppressWarnings("unchecked")
  private GiraphScenarioWrapper<I, V, E, M1, M2> loadGiraphScneario(GiraphScenario giraphScenario)
    throws ClassNotFoundException, IOException {
    Class<?> clazz = Class.forName(giraphScenario.getClassUnderTest());
    Class<? extends Computation<I, V, E, M1, M2>> classUnderTest =
        (Class<? extends Computation<I, V, E, M1, M2>>) castClassToUpperBound(clazz,
            Computation.class);

    Class<I> vertexIdClass =
        (Class<I>) castClassToUpperBound(Class.forName(giraphScenario.getVertexIdClass()),
            WritableComparable.class);

    Class<V> vertexValueClass =
        (Class<V>) castClassToUpperBound(Class.forName(giraphScenario.getVertexValueClass()),
            Writable.class);

    Class<E> edgeValueClass =
        (Class<E>) castClassToUpperBound(Class.forName(giraphScenario.getEdgeValueClass()),
            Writable.class);

    Class<M1> incomingMessageClass =
        (Class<M1>) castClassToUpperBound(Class.forName(giraphScenario.getIncomingMessageClass()),
            Writable.class);

    Class<M2> outgoingMessageClass =
        (Class<M2>) castClassToUpperBound(Class.forName(giraphScenario.getOutgoingMessageClass()),
            Writable.class);

    GiraphScenarioWrapper<I, V, E, M1, M2> giraphScenarioWrapper =
      new GiraphScenarioWrapper(classUnderTest, vertexIdClass,
        vertexValueClass, edgeValueClass, incomingMessageClass, outgoingMessageClass);

    ContextOrBuilder context = giraphScenario.getContextOrBuilder();
    GiraphScenarioWrapper<I, V, E, M1, M2>.ContextWrapper contextWrapper =
      giraphScenarioWrapper.new ContextWrapper();
    contextWrapper.setSuperstepNoWrapper(context.getSuperstepNo());
    I vertexId = newInstance(vertexIdClass);
    fromByteString(context.getVertexId(), vertexId);
    contextWrapper.setVertexIdWrapper(vertexId);

    V vertexValue = newInstance(vertexValueClass);
    fromByteString(context.getVertexValueBefore(), vertexValue);
    contextWrapper.setVertexValueBeforeWrapper(vertexValue);
    fromByteString(context.getVertexValueAfter(), vertexValue);
    contextWrapper.setVertexValueAfterWrapper(vertexValue);

    for (Neighbor neighbor : context.getNeighborList()) {
      I neighborId = newInstance(vertexIdClass);
      fromByteString(neighbor.getNeighborId(), neighborId);

      E edgeValue;
      if (neighbor.hasEdgeValue()) {
        edgeValue = newInstance(edgeValueClass);
        fromByteString(neighbor.getEdgeValue(), edgeValue);
      } else {
        edgeValue = null;
      }
      contextWrapper.addNeighborWrapper(neighborId, edgeValue);
    }
    for (int i = 0; i < context.getInMessageCount(); i++) {
      M1 msg = newInstance(incomingMessageClass);
      fromByteString(context.getInMessage(i), msg);
      contextWrapper.addIncomingMessageWrapper(msg);
    }

    for (OutMsg outmsg : context.getOutMessageList()) {
      I destinationId = newInstance(vertexIdClass);
      fromByteString(outmsg.getDestinationId(), destinationId);
      M2 msg = newInstance(outgoingMessageClass);
      fromByteString(outmsg.getMsgData(), msg);
      contextWrapper.addOutgoingMessageWrapper(destinationId, msg);
    }

    giraphScenarioWrapper.setContextWrapper(contextWrapper);
    return giraphScenarioWrapper;
  }

  public void save(String fileName, GiraphScenarioWrapper<I, V, E, M1, M2> scenarioWrapper)
      throws IOException {
    GiraphScenario giraphScenario = buildGiraphScnearioFromWrapper(scenarioWrapper);
    try (FileOutputStream output = new FileOutputStream(fileName)) {
      giraphScenario.writeTo(output);
      output.close();
    }
  }

  public void saveToHDFS(FileSystem fs, String fileName,
    GiraphScenarioWrapper<I, V, E, M1, M2> scenarioWrapper) throws IOException {
    GiraphScenario giraphScenario = buildGiraphScnearioFromWrapper(scenarioWrapper);
    Path pt=new Path(fileName);
    giraphScenario.writeTo(fs.create(pt, true).getWrappedStream());
  }

  private GiraphScenario buildGiraphScnearioFromWrapper(
    GiraphScenarioWrapper<I, V, E, M1, M2> scenarioWrapper) {

    GiraphScenario.Builder giraphScenarioBuilder = GiraphScenario.newBuilder();
    giraphScenarioBuilder.setClassUnderTest(scenarioWrapper.getClassUnderTest().getName());
    giraphScenarioBuilder.setVertexIdClass(scenarioWrapper.getVertexIdClass().getName());
    giraphScenarioBuilder.setVertexValueClass(scenarioWrapper.getVertexValueClass().getName());
    giraphScenarioBuilder.setEdgeValueClass(scenarioWrapper.getEdgeValueClass().getName());
    giraphScenarioBuilder.setIncomingMessageClass(scenarioWrapper.getIncomingMessageClass().getName());
    giraphScenarioBuilder.setOutgoingMessageClass(scenarioWrapper.getOutgoingMessageClass().getName());

    GiraphScenarioWrapper<I, V, E, M1, M2>.ContextWrapper contextWrapper = scenarioWrapper
      .getContextWrapper();
    Context.Builder contextBuilder = Context.newBuilder();
    contextBuilder.setSuperstepNo(contextWrapper.getSuperstepNoWrapper())
      .setVertexId(toByteString(contextWrapper.getVertexIdWrapper()))
      .setVertexValueBefore(toByteString(contextWrapper.getVertexValueBeforeWrapper()))
      .setVertexValueAfter(toByteString(contextWrapper.getVertexValueAfterWrapper()));

    for (GiraphScenarioWrapper<I, V, E, M1, M2>.ContextWrapper.NeighborWrapper neighbor :
      contextWrapper.getNeighborWrappers()) {
      Neighbor.Builder neighborBuilder = Neighbor.newBuilder();
      neighborBuilder.setNeighborId(toByteString(neighbor.getNbrId()));
      E edgeValue = neighbor.getEdgeValue();
      if (edgeValue != null) {
        neighborBuilder.setEdgeValue(toByteString(edgeValue));
      } else {
        neighborBuilder.clearEdgeValue();
      }
      contextBuilder.addNeighbor(neighborBuilder.build());
    }

    for (M1 msg : contextWrapper.getIncomingMessageWrappers()) {
      contextBuilder.addInMessage(toByteString(msg));
    }

    for (OutgoingMessageWrapper msg : contextWrapper.getOutgoingMessageWrappers()) {
      OutMsg.Builder outMsgBuilder = OutMsg.newBuilder();
      outMsgBuilder.setMsgData(toByteString(msg.message));
      outMsgBuilder.setDestinationId(toByteString(msg.destinationId));
      contextBuilder.addOutMessage(outMsgBuilder);
    }
    giraphScenarioBuilder.setContext(contextBuilder.build());   

    GiraphScenario giraphScenario = giraphScenarioBuilder.build();
    return giraphScenario;
  }

  @SuppressWarnings("unchecked")
  protected <U> Class<U> castClassToUpperBound(Class<?> clazz, Class<U> upperBound) {
    if (!upperBound.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("The class " + clazz.getName() + " is not a subclass of "
          + upperBound.getName());
    }
    return (Class<U>) clazz;
  }

  private static void fromByteString(ByteString byteString, Writable writable) {
    if (writable != null) {
      WritableUtils.readFieldsFromByteArray(byteString.toByteArray(), writable);
    }
  }

  private static ByteString toByteString(Writable writable) {
    return ByteString.copyFrom(WritableUtils.writeToByteArray(writable));
  }
  
  static <T> T newInstance(Class<T> theClass) {
    return NullWritable.class.isAssignableFrom(theClass) 
        ? null : ReflectionUtils.newInstance(theClass);
  }
}