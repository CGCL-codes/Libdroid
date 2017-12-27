package xiaowang.filebrowser.biz;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class CMDExecutor {
	public synchronized static String run( String workDir,String... cmd) {
		StringBuilder sb = new StringBuilder();
		try {
			if (cmd != null && workDir != null) {
				ProcessBuilder builder = new ProcessBuilder(cmd);
				builder.directory(new File(workDir));
				builder.redirectErrorStream(true);

				Process process = builder.start();
				InputStream in = process.getInputStream();
				BufferedInputStream bis = new BufferedInputStream(in);

				int len = -1;
				byte[] bytes = new byte[1024];
				while ((len = bis.read(bytes)) != -1) {
					sb.append(new String(bytes, 0, len));
				}
				bis.close();
				in.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sb.toString();
	}
}
