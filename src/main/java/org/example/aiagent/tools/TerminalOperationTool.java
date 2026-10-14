package org.example.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TerminalOperationTool {

    /**
     * 命令超时（秒）。
     * 原实现 process.waitFor() 无超时，一旦模型提交了阻塞命令
     * （如 ping、tail -f），线程会永久卡住，整轮推理再也不会结束。
     */
    private static final long TIMEOUT_SECONDS = 30;

    /**
     * 输出上限，防止 ls -R 这类命令的输出撑爆模型上下文。
     */
    private static final int MAX_OUTPUT_LENGTH = 8_000;

    /**
     * 当前系统是否为 Windows。
     * 原实现硬编码 cmd.exe，在 Linux 容器里直接抛 IOException
     * “Cannot run program \"cmd.exe\"”，导致该工具在 Docker 部署下完全不可用。
     */
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * cmd 把输出重定向到管道时仍按系统 ANSI 代码页写字节，中文 Windows 上是 GBK。
     * chcp 65001 只影响控制台，改不了这条管道，所以这里直接按 GBK 解码。
     */
    private static final Charset COMMAND_CHARSET = IS_WINDOWS
            ? Charset.forName("GBK")
            : StandardCharsets.UTF_8;

    @Tool(description = "Execute one shell command on the host OS. Obey the OS rule in the system prompt: Windows accepts only cmd.exe commands (dir, type, del); Linux and macOS accept Unix shell commands (ls, cat, rm).")
    public String executeTerminalCommand(
            @ToolParam(description = "A single command in the host shell syntax. On Windows use cmd syntax, not Unix syntax.") String command) {

        if (StrUtil.isBlank(command)) {
            return "[INVALID_ARGUMENT] 命令不能为空";
        }

        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            // 根据操作系统选择 shell：Windows 用 cmd.exe，类 Unix 用 sh
            List<String> commandParts = new ArrayList<>();
            if (IS_WINDOWS) {
                commandParts.add("cmd.exe");
                commandParts.add("/c");
            } else {
                commandParts.add("/bin/sh");
                commandParts.add("-c");
            }
            commandParts.add(command);

            ProcessBuilder builder = new ProcessBuilder(commandParts);
            // 合并 stderr 到 stdout，否则模型看不到报错信息
            builder.redirectErrorStream(true);
            process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), COMMAND_CHARSET))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > MAX_OUTPUT_LENGTH) {
                        output.append("...[输出过长已截断]\n");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                // 超时：强杀进程，否则会泄漏一个僵尸子进程
                process.destroyForcibly();
                return "[TIMEOUT] 命令执行超过 " + TIMEOUT_SECONDS + " 秒已被终止。输出:\n" + output;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("命令退出码: ").append(exitCode);
            }
        } catch (IOException e) {
            log.error("执行命令失败, command={}", command, e);
            return "[IO_ERROR] 命令执行失败: " + e.getMessage();
        } catch (InterruptedException e) {
            // 恢复中断标志：吞掉 InterruptedException 会破坏上层线程的取消语义
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return "[INTERRUPTED] 命令执行被中断";
        }
        return output.toString();
    }
}
