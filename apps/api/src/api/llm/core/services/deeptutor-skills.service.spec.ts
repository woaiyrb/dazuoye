import {
  buildReferenceAnswerCriterion,
  extractReferenceAnswer,
  parseVerdict,
  verdictToPoints,
} from "./deeptutor-skills.service";

describe("DeepTutor skill helpers", () => {
  it.each([
    ["✅ 正确\n理由", "correct"],
    ["⚠️ 部分正确\n理由", "partial"],
    ["❌ 不正确\n理由", "incorrect"],
    ["无法判断", "unknown"],
  ])("parses %s as %s", (feedback, expected) => {
    expect(parseVerdict(feedback)).toBe(expected);
  });

  it("maps verdicts to bounded points", () => {
    expect(verdictToPoints("correct", 10)).toBe(10);
    expect(verdictToPoints("partial", 10)).toBe(5);
    expect(verdictToPoints("incorrect", 10)).toBe(0);
    expect(verdictToPoints("unknown", 9)).toBe(5);
  });

  it("round-trips a reference answer through rubric criteria", () => {
    const answer = "先移项，再合并同类项，最后验算。";
    const scoring = {
      rubrics: [
        {
          criteria: [
            {
              description: buildReferenceAnswerCriterion(answer),
            },
          ],
        },
      ],
    };

    expect(extractReferenceAnswer(scoring)).toBe(answer);
  });
});
