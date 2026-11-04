import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { Prisma, PrismaClient } from "@prisma/client";
import dotenv from "dotenv";
import { type CreateUpdateQuestionRequestDto } from "../src/api/assignment/question/dto/create.update.question.request.dto";

const prisma = new PrismaClient();
// eslint-disable-next-line unicorn/prefer-module
dotenv.config({ path: path.join(__dirname, "../../../dev.env") });

const questions: CreateUpdateQuestionRequestDto[] = [
  {
    question: "计算：(-2) + 5 = ?",
    type: "SINGLE_CORRECT",
    totalPoints: 1,
    authorComment: "先确定符号，再计算绝对值。",
    choices: [
      { id: 1, choice: "2", isCorrect: false, points: 0 },
      { id: 2, choice: "3", isCorrect: true, points: 1 },
      { id: 3, choice: "-3", isCorrect: false, points: 0 },
      { id: 4, choice: "-7", isCorrect: false, points: 0 },
    ],
  },
  {
    question: "绝对值等于 3 的数只有 3。",
    type: "TRUE_FALSE",
    totalPoints: 1,
    choices: [
      { id: 1, choice: "false", isCorrect: true, points: 1 },
      { id: 2, choice: "true", isCorrect: false, points: 0 },
    ],
  },
  {
    question: "方程 3x = 12 的解是 x = ?",
    type: "SINGLE_CORRECT",
    totalPoints: 1,
    choices: [
      { id: 1, choice: "2", isCorrect: false, points: 0 },
      { id: 2, choice: "3", isCorrect: false, points: 0 },
      { id: 3, choice: "4", isCorrect: true, points: 1 },
      { id: 4, choice: "6", isCorrect: false, points: 0 },
    ],
  },
];

async function runMysqlRestore(sqlFilePath: string) {
  const database = process.env.MYSQL_DATABASE;
  const user = process.env.MYSQL_USER;
  const password = process.env.MYSQL_PASSWORD; // pragma: allowlist secret
  const host = process.env.MYSQL_HOST;
  const port = process.env.MYSQL_PORT ?? "3306";
  // eslint-disable-next-line unicorn/prefer-module
  const mysqlExe =
    process.env.MYSQL_EXE ||
    path.join(
      __dirname,
      "..",
      "..",
      "..",
      ".local-dev",
      "mysql",
      "bin",
      "mysql.exe",
    );
  execFileSync(
    mysqlExe,
    [
      `--host=${host}`,
      `--port=${port}`,
      `--user=${user}`,
      `--password=${password}`,
      "--protocol=TCP",
      database,
    ],
    {
      stdio: ["pipe", "inherit", "inherit"],
      input: fs.readFileSync(sqlFilePath),
    },
  );
}

async function main() {
  console.log("🌱 Starting database seed...");

  // eslint-disable-next-line unicorn/prefer-module
  const sqlFilePath = path.join(__dirname, "seed.sql");

  if (fs.existsSync(sqlFilePath)) {
    console.log("📁 Found seed.sql file - importing with the mysql client...");
    await runMysqlRestore(sqlFilePath);
    console.log("✅ Database seeded successfully from seed.sql!");
  } else {
    console.log("📝 No seed.sql found - creating sample assignment data...");
    await prisma.assignment.create({
      data: {
        name: "初一数学·有理数基础练习",
        type: "AI_GRADED",
        introduction:
          "通过选择题和判断题复习有理数加法、绝对值与一元一次方程。",
        instructions: `请完成 3 道基础题。选择题选择一个答案，判断题判断正误。`,
        gradingCriteriaOverview: `本作业共 3 分，每题 1 分，达到 60% 即为通过。AI 只负责生成讲评草稿，不决定最终对错和得分。

  对错和得分由系统根据标准答案计算，教师审核 AI 讲评后再发布。

  点击“开始作答”提交你的答案。`,
        graded: true,
        allotedTimeMinutes: 10,
        displayOrder: "RANDOM",
        showAssignmentScore: true,
        showQuestionScore: true,
        showSubmissionFeedback: true,
        numAttempts: undefined,
        timeEstimateMinutes: 10,
        passingGrade: 60,
        published: true,
        questions: {
          createMany: {
            data: questions.map((q) => ({
              ...q,
              scoring: q.scoring as unknown as Prisma.InputJsonValue,
              choices: q.choices as unknown as Prisma.InputJsonValue,
            })),
          },
        },
        groups: {
          create: [
            {
              group: {
                connectOrCreate: {
                  where: {
                    id: "text-group-id",
                  },
                  create: {
                    id: "text-group-id",
                  },
                },
              },
            },
          ],
        },
      },
    });
    console.log("✅ Sample assignment created successfully!");
  }
}

main()
  .catch((error) => {
    console.error("❌ Error during seeding:");
    console.error(error);
    // eslint-disable-next-line unicorn/no-process-exit
    process.exit(1);
  })
  .finally(() => {
    void prisma.$disconnect();
  });
