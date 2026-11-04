/**
 * Ensure the MySQL database referenced by DATABASE_URL exists.
 *
 * Uses the bundled `mysql` CLI (see .local-dev\mysql) so the project does not
 * need a Node MySQL driver just to bootstrap the schema.
 */
const { execFileSync } = require("node:child_process");
const path = require("node:path");

const url = process.env.DATABASE_URL || "";
const match = url.match(
  /^mysql:\/\/([^:/@]+)(?::([^@]*))?@([^:/?]+)(?::(\d+))?\/([^?]+)/,
);

if (!match) {
  console.error(
    `ensureDb: DATABASE_URL is not a mysql:// URL ("${url}"), skipping.`,
  );
  process.exit(0);
}

const [, user, password = "", host, port = "3306", database] = match;

const mysqlExe =
  process.env.MYSQL_EXE ||
  path.join(__dirname, "..", "..", ".local-dev", "mysql", "bin", "mysql.exe");

const args = [
  `--host=${host}`,
  `--port=${port}`,
  `--user=${user}`,
  `--password=${password}`,
  "--protocol=TCP",
  "-e",
  `CREATE DATABASE IF NOT EXISTS \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`,
];

try {
  execFileSync(mysqlExe, args, { stdio: "inherit" });
  console.log(`ensureDb: database "${database}" is ready on ${host}:${port}.`);
} catch (error) {
  console.error("ensureDb: failed to ensure database exists:", error.message);
  process.exit(1);
}
