package org.webreformatter.commons.json.rpc.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.webreformatter.commons.json.rpc.IRpcCallHandler.IRpcCallback;
import org.webreformatter.commons.json.rpc.RpcCallHandler;
import org.webreformatter.commons.json.rpc.RpcDispatcher;
import org.webreformatter.commons.json.rpc.RpcError;
import org.webreformatter.commons.json.rpc.RpcRequest;
import org.webreformatter.commons.json.rpc.RpcResponse;

/**
 * @author kotelnikov
 */
public abstract class AbstractJsonRpcServlet extends HttpServlet {

    private static final String ENCODING = "UTF-8";

    private static final String JSON_MIME_TYPE = "application/json";

    protected static final Logger LOG = Logger
        .getLogger(AbstractJsonRpcServlet.class.getName());

    private static final long serialVersionUID = -6602353013259628191L;

    protected RpcDispatcher fDispatcher = new RpcDispatcher();

    public AbstractJsonRpcServlet() {
    }

    protected int getWaitTimeout() {
        return 100;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            initDefaultListener(fDispatcher, config);
            initListeners(fDispatcher, config);
        } catch (Throwable t) {
            if (t instanceof ServletException) {
                throw (ServletException) t;
            } else {
                throw new ServletException(t);
            }
        }
    }

    public void initDefaultListener(
        RpcDispatcher dispatcher,
        ServletConfig config) {
        dispatcher.setDefaultHandler(new RpcCallHandler() {
            @Override
            protected RpcResponse doHandle(RpcRequest request) throws Exception {
                Object id = request.getId();
                if (id == null) {
                    id = newRequestId();
                }
                RpcResponse response = new RpcResponse()
                    .<RpcResponse> setId(id)
                    .<RpcResponse> setError(
                        RpcError.ERROR_METHOD_NOT_FOUND,
                        "Method not found");
                return response;
            }
        });
    }

    protected abstract void initListeners(
        RpcDispatcher dispatcher,
        ServletConfig config) throws Exception;

    private RpcRequest readRequest(HttpServletRequest req) throws IOException {
        String content = req.getParameter("content");
        if (content == null) {
            ServletInputStream input = req.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024 * 10];
                int len = 0;
                while ((len = input.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                byte[] array = out.toByteArray();
                content = new String(array, ENCODING);
            } finally {
                input.close();
            }
        }
        RpcRequest request = RpcRequest.FACTORY.newValue(content);
        return request;
    }

    public IOException reportError(String msg, Throwable t) {
        LOG.log(Level.WARNING, msg, t);
        if (t instanceof IOException) {
            return (IOException) t;
        }
        return new IOException(msg, t);
    }

    @Override
    protected void service(
        HttpServletRequest req,
        final HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setCharacterEncoding(ENCODING);
            resp.setCharacterEncoding(ENCODING);
            resp.setContentType(JSON_MIME_TYPE);
            RpcRequest request = readRequest(req);
            final RpcResponse[] result = { null };
            fDispatcher.handle(request, new IRpcCallback() {
                @Override
                public void finish(RpcResponse response) {
                    synchronized (result) {
                        result[0] = response;
                        result.notifyAll();
                    }
                }
            });
            int waitTimeout = getWaitTimeout();
            while (true) {
                synchronized (result) {
                    if (result[0] != null) {
                        break;
                    }
                    result.wait(waitTimeout);
                }
            }
            final ServletOutputStream out = resp.getOutputStream();
            out.write(result[0].toString().getBytes(ENCODING));
        } catch (Exception t) {
            throw reportError(
                "Can not download a resource with the specified URL",
                t);
        }
    }

}
