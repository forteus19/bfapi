package dev.vuis.bfapi;

import dev.vuis.bfapi.auth.MicrosoftAuth;
import dev.vuis.bfapi.auth.MinecraftAuth;
import dev.vuis.bfapi.auth.MsCodeWrapper;
import dev.vuis.bfapi.auth.XblAuth;
import dev.vuis.bfapi.auth.XstsAuth;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Main {
	private static final int PORT = Integer.parseInt(Util.getEnvOrThrow("PORT"));
	private static final String MS_CLIENT_ID = Util.getEnvOrThrow("MS_CLIENT_ID");
	private static final String MS_CLIENT_SECRET_FILE = System.getenv("MS_CLIENT_SECRET_FILE");
	private static final String MS_REDIRECT_HOST = Util.getEnvOrThrow("MS_REDIRECT_HOST");
	private static final boolean MS_PASTE_REDIRECT = Boolean.parseBoolean(Util.getEnvOrThrow("MS_PASTE_REDIRECT"));
	private static final String BF_VERSION = Util.getEnvOrThrow("BF_VERSION");
	private static final String BF_VERSION_HASH = Util.getEnvOrThrow("BF_VERSION_HASH");
	private static final String BF_HARDWARE_ID = Util.getEnvOrThrow("BF_HARDWARE_ID");

    private Main() {
    }

	@SneakyThrows
    public static void main(String[] args) {
		log.info("starting HTTP server");

		CompletableFuture<String> msCodeFuture = null;
		String msState = null;
		MsCodeWrapper msCodeWrapper = null;
		if (!MS_PASTE_REDIRECT) {
			msCodeFuture = new CompletableFuture<>();
			msState = MicrosoftAuth.randomState();
			msCodeWrapper = new MsCodeWrapper(msCodeFuture, msState);
		}
		startHttpServer(msCodeWrapper);

		String msClientSecret = null;
		if (MS_CLIENT_SECRET_FILE != null) {
			try {
				msClientSecret = Files.readString(Path.of(MS_CLIENT_SECRET_FILE));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
        MicrosoftAuth msAuth = new MicrosoftAuth(
			MS_CLIENT_ID,
			msClientSecret,
			MS_REDIRECT_HOST + (MS_PASTE_REDIRECT ? "" : BfApiInboundHandler.AUTH_CALLBACK_PATH)
		);

		log.info("microsoft auth URL: {}", msAuth.getAuthUri(MicrosoftAuth.XBOX_LIVE_SCOPE, msState));

		String msAuthorizationCode;
		if (MS_PASTE_REDIRECT) {
			log.info("paste redirected location:");
			String redirectInput = new Scanner(System.in).nextLine();
			msAuthorizationCode = parseRedirectResult(redirectInput);
		} else {
			msAuthorizationCode = msCodeFuture.get();
		}
		msAuth.redeemCode(msAuthorizationCode, false);

		XblAuth xblAuth = new XblAuth(msAuth);
		XstsAuth xstsAuth = new XstsAuth(xblAuth);
		MinecraftAuth mcAuth = new MinecraftAuth(xstsAuth);

		log.info(mcAuth.tokenOrRefresh());
    }

	private static void startHttpServer(MsCodeWrapper msCodeWrapper) {
		EventLoopGroup elg = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(elg)
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(msCodeWrapper));

		bootstrap.bind(PORT).syncUninterruptibly();
	}

	private static String parseRedirectResult(String uri) {
		QueryStringDecoder qs = new QueryStringDecoder(uri);
		if (!qs.parameters().containsKey("code")) {
			throw new IllegalArgumentException("uri does not have code query parameter");
		}
		return qs.parameters().get("code").getFirst();
	}
}
