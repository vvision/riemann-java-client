package com.aphyr.riemann.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;

import com.aphyr.riemann.Proto.Event;
import com.aphyr.riemann.Proto.Query;
import com.aphyr.riemann.Proto.Msg;

public abstract class AbstractRiemannClient implements DualTransport {
  protected volatile RiemannScheduler scheduler = null;

  // Asserts that the message is OK; if not, throws a ServerError.
  public static Msg validate(final Msg message) throws ServerError {
    if (message.hasOk() && !message.getOk()) {
      throw new ServerError(message.getError());
    }
    return message;
  }

  public abstract Msg sendRecvMessage(final Msg m) throws IOException;
  public abstract Msg sendMaybeRecvMessage(final Msg m) throws IOException;
  public abstract IPromise<Msg> aSendRecvMessage(final Msg m);
  public abstract IPromise<Msg> aSendMaybeRecvMessage(final Msg m);
  public abstract void connect() throws IOException;
  public abstract boolean isConnected();
  public abstract void disconnect() throws IOException;
  public abstract void reconnect() throws IOException;
  public abstract void flush() throws IOException;

  // A new event DSL bound to this client.
  public EventDSL event() {
    return new EventDSL(this);
  }

  // Sends events and checks the server's response. Will throw IOException for
  // network failures, ServerError for error responses from Riemann. Returns
  // true if events acknowledged.
  public Boolean sendEventsWithAck(final List<Event> events) throws IOException, MsgTooLargeException {
    validate(
        sendRecvMessage(
          Msg.newBuilder()
          .addAllEvents(events)
          .build()));
    return true;
  }

  public IPromise<Boolean> aSendEventsWithAck(final List<Event> events) {
    final IPromise<Msg> p = aSendMaybeRecvMessage(
        Msg.newBuilder()
        .addAllEvents(events)
        .build());

    return new IPromise<Boolean>() {
      public void deliver(Object value) { }
      public Boolean deref() throws IOException {
        validate(p.deref());
        return true;
      }
      public Boolean deref(long time, TimeUnit unit) throws IOException {
        validate(p.deref(time, unit));
        return true;
      }
      public Boolean deref(long time, TimeUnit unit, Boolean timeoutValue) throws IOException {
        if (null != validate(p.deref(time, unit, null))) {
          return true;
        } else {
          return false;
        }
      }
    };
  }

  // Sends events and checks the server's response. Will throw IOException for
  // network failures, ServerError for error responses from Riemann. Returns
  // true if events acknowledged.
  public Boolean sendEventsWithAck(final Event... events) throws IOException, ServerError, MsgTooLargeException {
    return sendEventsWithAck(Arrays.asList(events));
  }

  public IPromise<Boolean> aSendEventsWithAck(final Event... events) {
    return aSendEventsWithAck(Arrays.asList(events));
  }

  // Sends events in fire-and-forget fashion. Doesn't check server response,
  // swallows all exceptions silently. No guarantees on delivery.
  public void sendEvents(final List<Event> events) {
    try {
      sendMaybeRecvMessage(
         Msg.newBuilder()
          .addAllEvents(events)
          .build()
      );
    } catch (IOException e) {
      // Fuck it.
    }
  }

  // Sends events in fire-and-forget fashion. Doesn't check server response,
  // swallows all exceptions silently. No guarantees on delivery.
  public void sendEvents(final Event... events) {
    sendEvents(Arrays.asList(events));
  }

  // Send an Exception event, with state "error" and tagged
  // "exception". The event will also be tagged with the exception class name.
  // Description includes the exception class and stack trace.
  public void sendException(String service, Throwable t) {
      final StringBuilder desc = new StringBuilder();
      desc.append(t.toString());
      desc.append("\n\n");
      for (StackTraceElement e : t.getStackTrace()) {
          desc.append(e);
          desc.append("\n");
      }

      event().service(service)
              .state("error")
              .tag("exception")
              .tag(t.getClass().getSimpleName())
              .description(desc.toString())
              .send();
  }

  public List<Event> query(String q) throws IOException, ServerError, MsgTooLargeException {
    Msg m = sendRecvMessage(Msg.newBuilder()
        .setQuery(
          Query.newBuilder().setString(q).build())
        .build());

    validate(m);

    return Collections.unmodifiableList(m.getEventsList());
  }

  // Returns the scheduler for this client. Creates the scheduler on first use.
  public synchronized RiemannScheduler scheduler() {
      if (scheduler == null) {
          scheduler = new RiemannScheduler(this);
      }
      return scheduler;
  }

  // Set up recurring tasks on this client's scheduler.
  // This may be the lowest entropy for any code I've ever written.
  public ScheduledFuture every(long interval, Runnable f) { return scheduler().every(interval, f); }
  public ScheduledFuture every(long interval, RiemannScheduler.Task f) { return scheduler().every(interval, f); }
  public ScheduledFuture every(long interval, TimeUnit unit, Runnable f) { return scheduler().every(interval, unit, f); }
  public ScheduledFuture every(long interval, TimeUnit unit, RiemannScheduler.Task f) { return scheduler().every(interval, unit, f); }
  public ScheduledFuture every(long interval, long delay, Runnable f) { return scheduler().every(interval, delay, f); }
  public ScheduledFuture every(long interval, long delay, RiemannScheduler.Task f) { return scheduler().every(interval, delay, f); }
  public ScheduledFuture every(long interval, long delay, TimeUnit unit, Runnable f) { return scheduler().every(interval, delay, unit, f); }
  public ScheduledFuture every(long interval, long delay, TimeUnit unit, RiemannScheduler.Task f) { return scheduler().every(interval, delay, unit, f); }
}
