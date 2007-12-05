package org.jboss.cx.remoting.jrpp.msg;

/**
 *
 */
public interface JrppMessageVisitor {
    void visit(JrppCloseServiceMessage jrppCloseServiceMessage);

    void visit(JrppCloseContextMessage jrppServiceCloseContextMessage);

    void visit(JrppCloseRequestMessage jrppCloseRequestMessage);

    void visit(JrppServiceActivateMessage jrppServiceActivateMessage);

    void visit(JrppServiceRequestMessage jrppServiceRequestMessage);

    void visit(JrppCloseStreamMessage jrppCloseStreamMessage);

    void visit(JrppRequest jrppRequest);

    void visit(JrppReply jrppReply);

    void visit(JrppExceptionMessage jrppExceptionMessage);

    void visit(JrppCancelAcknowledgeMessage jrppCancelAcknowledgeMessage);

    void visit(JrppCancelRequestMessage jrppCancelRequestMessage);

    void visit(JrppStreamDataMessage jrppStreamDataMessage);
}
