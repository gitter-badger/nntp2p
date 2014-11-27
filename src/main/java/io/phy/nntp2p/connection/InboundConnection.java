package io.phy.nntp2p.connection;

import io.phy.nntp2p.exceptions.ArticleNotFoundException;
import io.phy.nntp2p.exceptions.NntpUnknownCommandException;
import io.phy.nntp2p.protocol.Article;
import io.phy.nntp2p.protocol.ClientCommand;
import io.phy.nntp2p.protocol.NNTPReply;
import io.phy.nntp2p.protocol.ServerResponse;
import io.phy.nntp2p.proxy.ArticleProxy;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class InboundConnection extends BaseConnection implements Runnable
{
    private ArticleProxy proxy;
    private boolean isPeer;

    private boolean listening = true;

    protected final static Logger log = Logger.getLogger(InboundConnection.class.getName());

    public InboundConnection(Socket socket, ArticleProxy proxy) throws IOException {
        BindToSocket(socket);

        this.proxy = proxy;
        isPeer = false;
    }

    @Override
    public void run() {
        try {
            // First thing we have to do is publish a welcome message!
            WriteData(new ServerResponse(NNTPReply.SERVER_READY_POSTING_NOT_ALLOWED));

            while(socket.isConnected() && listening) {
                ClientCommand command = null;
                String rawInput = reader.readLineString();
                if( rawInput == null ) { break; }
                try {
                    command = ClientCommand.Parse(rawInput);
                    DispatchCommand(command);
                } catch (NntpUnknownCommandException e) {
                    log.info("Unknown unknown command: "+rawInput);
                    WriteData(new ServerResponse(NNTPReply.COMMAND_NOT_RECOGNIZED));
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if( socket.isConnected() ) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }
        }
    }

    private void DispatchCommand(ClientCommand command) throws IOException, NntpUnknownCommandException {
        switch (command.getCommand()) {
            case QUIT:
                cmdQuit();
                break;

            case BODY:
                cmdBody(command);
                break;

            case PEER:
                cmdPeer(command);
                break;

            default:
                throw new NntpUnknownCommandException();
        }
    }

    private void cmdQuit() throws IOException {
        listening = false;
        WriteData(new ServerResponse(NNTPReply.CLOSING_CONNECTION));
    }

    private void cmdPeer(ClientCommand command) throws IOException {
        log.info("Client recognised as a downstream cache peer: "+socket);
        isPeer = true;
        WriteData(new ServerResponse(NNTPReply.SERVER_READY_POSTING_NOT_ALLOWED));
    }

    private void cmdBody(ClientCommand command) throws IOException {
        // Do some validation over the article
        if( command.getArguments().size() > 1 ) {
            log.fine("Invalid ARTICLE request: "+command.ToNntpString());
            WriteData(new ServerResponse(NNTPReply.COMMAND_SYNTAX_ERROR));
            return;
        }

        String messageId = command.getArguments().get(0);
        Article articleData;
        try {
            articleData = proxy.GetArticle(messageId,isPeer);
        } catch (ArticleNotFoundException e) {
            log.fine("ARTICLE not found: " + messageId);
            WriteData(new ServerResponse(NNTPReply.NO_SUCH_ARTICLE_FOUND));
            return;
        }
        if (articleData == null) {
            log.fine("BODY got back NULL: " + messageId);
        }

        // TODO: Have hit a case here where articleData is null
        ServerResponse response = new ServerResponse(NNTPReply.ARTICLE_RETRIEVED_BODY_FOLLOWS);
        response.addArg(0);
        response.addArg(messageId);

        WriteData(response);
        WriteArticleBody(articleData);
    }
}
