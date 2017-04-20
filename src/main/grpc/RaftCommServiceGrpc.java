package grpc;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.2.0)",
    comments = "Source: raft.proto")
public final class RaftCommServiceGrpc {

  private RaftCommServiceGrpc() {}

  public static final String SERVICE_NAME = "RaftCommService";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<grpc.Raft.RequestVoteReq,
      grpc.Raft.RequestVoteResp> METHOD_REQUEST_VOTE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "RaftCommService", "RequestVote"),
          io.grpc.protobuf.ProtoUtils.marshaller(grpc.Raft.RequestVoteReq.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(grpc.Raft.RequestVoteResp.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<grpc.Raft.AppendEntriesReq,
      grpc.Raft.AppendEntriesResp> METHOD_APPEND_ENTRIES =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "RaftCommService", "AppendEntries"),
          io.grpc.protobuf.ProtoUtils.marshaller(grpc.Raft.AppendEntriesReq.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(grpc.Raft.AppendEntriesResp.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<grpc.Raft.ClientReq,
      grpc.Raft.ClientResp> METHOD_CLIENT_OPERATE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "RaftCommService", "ClientOperate"),
          io.grpc.protobuf.ProtoUtils.marshaller(grpc.Raft.ClientReq.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(grpc.Raft.ClientResp.getDefaultInstance()));

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RaftCommServiceStub newStub(io.grpc.Channel channel) {
    return new RaftCommServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RaftCommServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new RaftCommServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary and streaming output calls on the service
   */
  public static RaftCommServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new RaftCommServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class RaftCommServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void requestVote(grpc.Raft.RequestVoteReq request,
        io.grpc.stub.StreamObserver<grpc.Raft.RequestVoteResp> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_REQUEST_VOTE, responseObserver);
    }

    /**
     */
    public void appendEntries(grpc.Raft.AppendEntriesReq request,
        io.grpc.stub.StreamObserver<grpc.Raft.AppendEntriesResp> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_APPEND_ENTRIES, responseObserver);
    }

    /**
     */
    public void clientOperate(grpc.Raft.ClientReq request,
        io.grpc.stub.StreamObserver<grpc.Raft.ClientResp> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_CLIENT_OPERATE, responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_REQUEST_VOTE,
            asyncUnaryCall(
              new MethodHandlers<
                grpc.Raft.RequestVoteReq,
                grpc.Raft.RequestVoteResp>(
                  this, METHODID_REQUEST_VOTE)))
          .addMethod(
            METHOD_APPEND_ENTRIES,
            asyncUnaryCall(
              new MethodHandlers<
                grpc.Raft.AppendEntriesReq,
                grpc.Raft.AppendEntriesResp>(
                  this, METHODID_APPEND_ENTRIES)))
          .addMethod(
            METHOD_CLIENT_OPERATE,
            asyncUnaryCall(
              new MethodHandlers<
                grpc.Raft.ClientReq,
                grpc.Raft.ClientResp>(
                  this, METHODID_CLIENT_OPERATE)))
          .build();
    }
  }

  /**
   */
  public static final class RaftCommServiceStub extends io.grpc.stub.AbstractStub<RaftCommServiceStub> {
    private RaftCommServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private RaftCommServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RaftCommServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new RaftCommServiceStub(channel, callOptions);
    }

    /**
     */
    public void requestVote(grpc.Raft.RequestVoteReq request,
        io.grpc.stub.StreamObserver<grpc.Raft.RequestVoteResp> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_REQUEST_VOTE, getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void appendEntries(grpc.Raft.AppendEntriesReq request,
        io.grpc.stub.StreamObserver<grpc.Raft.AppendEntriesResp> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_APPEND_ENTRIES, getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void clientOperate(grpc.Raft.ClientReq request,
        io.grpc.stub.StreamObserver<grpc.Raft.ClientResp> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_CLIENT_OPERATE, getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class RaftCommServiceBlockingStub extends io.grpc.stub.AbstractStub<RaftCommServiceBlockingStub> {
    private RaftCommServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private RaftCommServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RaftCommServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new RaftCommServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public grpc.Raft.RequestVoteResp requestVote(grpc.Raft.RequestVoteReq request) {
      return blockingUnaryCall(
          getChannel(), METHOD_REQUEST_VOTE, getCallOptions(), request);
    }

    /**
     */
    public grpc.Raft.AppendEntriesResp appendEntries(grpc.Raft.AppendEntriesReq request) {
      return blockingUnaryCall(
          getChannel(), METHOD_APPEND_ENTRIES, getCallOptions(), request);
    }

    /**
     */
    public grpc.Raft.ClientResp clientOperate(grpc.Raft.ClientReq request) {
      return blockingUnaryCall(
          getChannel(), METHOD_CLIENT_OPERATE, getCallOptions(), request);
    }
  }

  /**
   */
  public static final class RaftCommServiceFutureStub extends io.grpc.stub.AbstractStub<RaftCommServiceFutureStub> {
    private RaftCommServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private RaftCommServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RaftCommServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new RaftCommServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<grpc.Raft.RequestVoteResp> requestVote(
        grpc.Raft.RequestVoteReq request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_REQUEST_VOTE, getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<grpc.Raft.AppendEntriesResp> appendEntries(
        grpc.Raft.AppendEntriesReq request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_APPEND_ENTRIES, getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<grpc.Raft.ClientResp> clientOperate(
        grpc.Raft.ClientReq request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_CLIENT_OPERATE, getCallOptions()), request);
    }
  }

  private static final int METHODID_REQUEST_VOTE = 0;
  private static final int METHODID_APPEND_ENTRIES = 1;
  private static final int METHODID_CLIENT_OPERATE = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final RaftCommServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(RaftCommServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REQUEST_VOTE:
          serviceImpl.requestVote((grpc.Raft.RequestVoteReq) request,
              (io.grpc.stub.StreamObserver<grpc.Raft.RequestVoteResp>) responseObserver);
          break;
        case METHODID_APPEND_ENTRIES:
          serviceImpl.appendEntries((grpc.Raft.AppendEntriesReq) request,
              (io.grpc.stub.StreamObserver<grpc.Raft.AppendEntriesResp>) responseObserver);
          break;
        case METHODID_CLIENT_OPERATE:
          serviceImpl.clientOperate((grpc.Raft.ClientReq) request,
              (io.grpc.stub.StreamObserver<grpc.Raft.ClientResp>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static final class RaftCommServiceDescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier {
    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return grpc.Raft.getDescriptor();
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (RaftCommServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RaftCommServiceDescriptorSupplier())
              .addMethod(METHOD_REQUEST_VOTE)
              .addMethod(METHOD_APPEND_ENTRIES)
              .addMethod(METHOD_CLIENT_OPERATE)
              .build();
        }
      }
    }
    return result;
  }
}
