package org.example;

import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

// from: https://github.com/soot-oss/soot/blob/3966f565db6dc2882c3538ffc39e44f4c14b5bcf/src/main/java/soot/util/EscapedWriter.java
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997 - 1999 Raja Vallee-Rai
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
/**
 * A FilterWriter which catches to-be-escaped characters (<code>\\unnnn</code>) in the input and substitutes their escaped
 * representation. Used for Soot output.
 */
class EscapedWriter extends FilterWriter {
    /** Convenience field containing the system's line separator. */
    public final String lineSeparator = System.getProperty("line.separator");
    private final int cr = lineSeparator.charAt(0);
    private final int lf = (lineSeparator.length() == 2) ? lineSeparator.charAt(1) : -1;

    /** Constructs an EscapedWriter around the given Writer. */
    public EscapedWriter(Writer fos) {
        super(fos);
    }

    private final StringBuffer mini = new StringBuffer();

    /** Print a single character (unsupported). */
    public void print(int ch) throws IOException {
        write(ch);
        throw new RuntimeException();
    }

    /** Write a segment of the given String. */
    public void write(String s, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            write(s.charAt(i));
        }
    }

    /** Write a single character. */
    public void write(int ch) throws IOException {
        if (ch >= 32 && ch <= 126 || ch == cr || ch == lf || ch == ' ') {
            super.write(ch);
            return;
        }

        mini.setLength(0);
        mini.append(Integer.toHexString(ch));

        while (mini.length() < 4) {
            mini.insert(0, "0");
        }

        mini.insert(0, "\\u");
        for (int i = 0; i < mini.length(); i++) {
            super.write(mini.charAt(i));
        }
    }
}

enum OcrMode {
    LOCAL_PROCESS,  // 本地进程
    SOCKET_SERVER  // 套接字服务器
}

class OcrCode {
    public static final int OK = 100;
    public static final int NO_TEXT = 101;
}

class OcrEntry {
    String text;
    int[][] box;
    double score;

    @Override
    public String toString() {
        return "RecognizedText{" +
                "text='" + text + '\'' +
                ", box=" + Arrays.toString(box) +
                ", score=" + score +
                '}';
    }
}

class OcrResponse {
    int code;
    OcrEntry[] data;
    String msg;
    String hotUpdate;

    @Override
    public String toString() {
        return "OcrResponse{" +
                "code=" + code +
                ", data=" + Arrays.toString(data) +
                ", msg='" + msg + '\'' +
                ", hotUpdate='" + hotUpdate + '\'' +
                '}';
    }

    public OcrResponse() {
    }

    public OcrResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}

public class Ocr implements AutoCloseable {
    // 公共
    Gson gson;
    boolean ocrReady = false;
    Map<String, Object> arguments;
    BufferedReader reader;
    BufferedWriter writer;
    OcrMode mode;

    // 本地进程模式
    Process process;
    File exePath;


    // 套接字服务器模式
    String serverAddr;
    int serverPort;
    Socket clientSocket;
    boolean isLoopback = false;

    /**
     * 使用套接字模式初始化
     * @param serverAddr
     * @param serverPort
     * @param arguments
     * @throws IOException
     */
    public Ocr(String serverAddr, int serverPort, Map<String, Object> arguments) throws IOException {
        this.mode = OcrMode.SOCKET_SERVER;
        this.arguments = arguments;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        checkIfLoopback();
        initOcr();
    }

    /**
     * 使用本地进程模式初始化
     * @param exePath
     * @param arguments
     * @throws IOException
     */
    public Ocr(File exePath, Map<String, Object> arguments) throws IOException {
        this.mode = OcrMode.LOCAL_PROCESS;
        this.arguments = arguments;
        this.exePath = exePath;
        initOcr();
    }

    private void initOcr() throws IOException {
        gson = new Gson();

        List<String> commandList = new ArrayList<>();
        if (arguments != null) {
            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                commandList.add("--" + entry.getKey() + "=" + entry.getValue().toString());
            }
        }

        for (String c: commandList) {
            if (!StandardCharsets.US_ASCII.newEncoder().canEncode(c)) {
                throw new IllegalArgumentException("参数不能含有非 ASCII 字符");
            }
        }

        System.out.println("当前参数：" + (commandList.isEmpty() ? "空": commandList));


        switch (this.mode) {
            case LOCAL_PROCESS: {
                File workingDir = exePath.getParentFile();
                commandList.add(0, exePath.toString());
                ProcessBuilder pb = new ProcessBuilder(commandList);
                pb.directory(workingDir);
                pb.redirectErrorStream(true);
                process = pb.start();

                InputStream stdout = process.getInputStream();
                OutputStream stdin = process.getOutputStream();
                reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(stdin, StandardCharsets.UTF_8));
                String line = "";
                ocrReady = false;
                while (!ocrReady) {
                    line = reader.readLine();
                    if (line.contains("OCR init completed")) {
                        ocrReady = true;
                    }
                }
                System.out.println("初始化OCR成功");
                break;
            }
            case SOCKET_SERVER: {
                clientSocket = new Socket(serverAddr, serverPort);
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
                ocrReady = true;
                System.out.println("已连接到OCR套接字服务器，假设服务器已初始化成功");
                break;
            }
        }


    }

    /**
     * 使用图片路径进行 OCR
     * @param imgFile
     * @return
     * @throws IOException
     */
    public OcrResponse runOcr(File imgFile) throws IOException {
        if (mode == OcrMode.SOCKET_SERVER && !isLoopback) {
            System.out.println("套接字模式下服务器不在本地，发送路径可能失败");
        }
        Map<String, String> reqJson = new HashMap<>();
        reqJson.put("image_path", imgFile.toString());
        return this.sendJsonToOcr(reqJson);
    }

    /**
     * 使用剪贴板中图片进行 OCR
     * @return
     * @throws IOException
     */
    public OcrResponse runOcrOnClipboard() throws IOException {
        if (mode == OcrMode.SOCKET_SERVER && !isLoopback) {
            System.out.println("套接字模式下服务器不在本地，发送剪贴板可能失败");
        }
        Map<String, String> reqJson = new HashMap<>();
        reqJson.put("image_path", "clipboard");
        return this.sendJsonToOcr(reqJson);
    }

    /**
     * 使用 Base64 编码的图片进行 OCR
     * @param base64str
     * @return
     * @throws IOException
     */
    public OcrResponse runOcrOnImgBase64(String base64str) throws IOException {
        Map<String, String> reqJson = new HashMap<>();
        reqJson.put("image_base64", base64str);
        return this.sendJsonToOcr(reqJson);
    }

    /**
     * 使用图片 Byte 数组进行 OCR
     * @param fileBytes
     * @return
     * @throws IOException
     */
    public OcrResponse runOcrOnImgBytes(byte[] fileBytes) throws IOException {
        return this.runOcrOnImgBase64(Base64.getEncoder().encodeToString(fileBytes));
    }

    private OcrResponse sendJsonToOcr(Map<String, String> reqJson) throws IOException {
        if (!isAlive()) {
            throw new RuntimeException("OCR进程已经退出或连接已断开");
        }
        StringWriter sw = new StringWriter();
        EscapedWriter ew = new EscapedWriter(sw);
        gson.toJson(reqJson, ew);

        // 重建 socket，修复长时间无请求时 socket 断开（Software caused connection abort: socket write error ）
        // https://github.com/hiroi-sora/PaddleOCR-json/issues/106
        if (OcrMode.SOCKET_SERVER == mode) {
            writer.close();
            reader.close();
            clientSocket.close();
            clientSocket = new Socket(serverAddr, serverPort);
            clientSocket.setKeepAlive(true);
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
        }

        writer.write(sw.getBuffer().toString());
        writer.write("\r\n");
        writer.flush();
        String resp = reader.readLine();
        System.out.println(resp);

        Map rawJsonObj = gson.fromJson(resp, Map.class);
        if (rawJsonObj.get("data") instanceof String) {
            return new OcrResponse((int)Double.parseDouble(rawJsonObj.get("code").toString()), rawJsonObj.get("data").toString());
        }

        return gson.fromJson(resp, OcrResponse.class);
    }


    private void checkIfLoopback() {
        if (this.mode != OcrMode.SOCKET_SERVER) return;
        try {
            InetAddress address = InetAddress.getByName(serverAddr);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(address);
            if (networkInterface != null && networkInterface.isLoopback()) {
                this.isLoopback = true;
            } else {
                this.isLoopback = false;
            }
        } catch (Exception e) {
            // 非关键路径
            System.out.println("套接字模式，未能确认服务端是否在本地");
        }
        System.out.println("套接字模式下，服务端在本地：" + isLoopback);
    }

    private boolean isAlive() {
        switch (this.mode) {
            case LOCAL_PROCESS:
                return process.isAlive();
            case SOCKET_SERVER:
                return clientSocket.isConnected();
        }
        return false;
    }


    @Override
    public void close() {
        if (isAlive()) {
            switch (this.mode) {
                case LOCAL_PROCESS: {
                    process.destroy();
                    break;
                }
                case SOCKET_SERVER: {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

}
