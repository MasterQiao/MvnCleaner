package per.masterqiao.mvncleaner;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

public class MvnCleaner {

	public static void main(String[] args) {
		CommandLineParser cliParser = new DefaultParser();
		Options opts = new Options();
		opts.addRequiredOption("d", null, true, "mvn repository directory");
		opts.addOption("D", false, "delete directly");
		CommandLine cli = null;
		try {
			cli = cliParser.parse(opts, args);
		} catch (ParseException e) {
			e.printStackTrace();
			return;
		}

		String rootPath = null;
		if (cli.hasOption("d")) {
			rootPath = cli.getOptionValue("d");
		} else {
			System.err.println("Must specify mvn repository directory with -d xxx");
			return;
		}

		File rootFile = new File(rootPath);
		if (!rootFile.exists()) {
			System.err.println("Invalid mvn repository directory: " + rootPath);
		}

		boolean delDirectly = cli.hasOption("D");

		MessageDigest sha1Md = DigestUtils.getSha1Digest();
		DigestUtils digestUtils = new DigestUtils(sha1Md);

		List<File> toDels = new ArrayList<>();
		Deque<File> dirs = new ArrayDeque<>();
		dirs.push(rootFile);

		while (!dirs.isEmpty()) {
			File dir = dirs.pop();
			File[] fs = dir.listFiles();
			for (File file : fs) {
				String filename = file.getName();
				if (file.isDirectory()) {
					dirs.push(file);
				} else if (!filename.endsWith(".sha1")) {
					File verifyFile = new File(dir, filename + ".sha1");
					if (!verifyFile.exists()) {
						System.out.println("[Alone] " + file.getAbsolutePath());
						toDels.add(file);
					} else {
						String actual = "actual";
						String expected = "expected";
						try {
							actual = digestUtils.digestAsHex(file);
							expected = FileUtils.readFileToString(verifyFile, "UTF-8");
						} catch (IOException e) {
							e.printStackTrace();
						}
						if (!expected.startsWith(actual)) {
							System.out.println("[Corrupted] " + file.getAbsolutePath());
							toDels.add(file);
							toDels.add(verifyFile);
						}
					}
				} else {
					String dataFilename = filename.substring(0, filename.length() - ".sha1".length());
					File dataFile = new File(dir, dataFilename);
					if (!dataFile.exists()) {
						System.out.println("[Alone] " + file.getAbsolutePath());
						toDels.add(file);
					}
				}
			}
		}

		if (!delDirectly) {
			System.out.println("delete?(y/n)");
			try {
				if ('y' != System.in.read()) {
					return;
				}
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		for (File f : toDels) {
			if (f.delete()) {
				System.out.println("[Deleted] " + f.getAbsolutePath());
			} else {
				System.out.println("[FailedDel] " + f.getAbsolutePath());
			}
		}

	}

}
