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
		opts.addOption("c", true, "contents to delete");

		CommandLine cli = null;
		try {
			cli = cliParser.parse(opts, args);
		} catch (ParseException e) {
			e.printStackTrace();
			return;
		}

		String rootPath = null;
		if (!cli.hasOption("d")) {
			System.err.println("Must specify mvn repository directory with -d xxx");
			return;
		}
		rootPath = cli.getOptionValue("d");

		File rootFile = new File(rootPath);
		if (!rootFile.exists()) {
			System.err.println("Invalid mvn repository directory: " + rootPath);
		}

		boolean delDirectly = cli.hasOption("D");

		// L lastUpadated files
		// R remote.repositories files
		// A Alone files without sha1 file
		// C corrupted files
		String contents = cli.hasOption("c") ? cli.getOptionValue("c") : "LRAC";
		boolean delL = contents.contains("L");
		boolean delR = contents.contains("R");
		boolean delA = contents.contains("A");
		boolean delC = contents.contains("C");

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
					continue;
				}

				if (filename.endsWith("lastUpdated.properties")) {
					if (delL) {
						toDels.add(file);
					}
					continue;
				}

				if (filename.endsWith("_remote.repositories")) {
					if (delR) {
						toDels.add(file);
					}
					continue;
				}

				if (!filename.endsWith(".sha1")) {
					File verifyFile = new File(dir, filename + ".sha1");
					if (verifyFile.exists()) {
						if (!delC) {
							continue;
						}
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
					} else {
						System.out.println("[Alone] " + file.getAbsolutePath());
						if (delA) {
							toDels.add(file);
						}
					}
				} else {
					String dataFilename = filename.substring(0, filename.length() - ".sha1".length());
					File dataFile = new File(dir, dataFilename);
					if (!dataFile.exists()) {
						System.out.println("[Alone] " + file.getAbsolutePath());
						if (delA) {
							toDels.add(file);
						}
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
