param(
  [string]$BaseUrl = "http://127.0.0.1:8082",
  [string]$MysqlClient = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
  [string]$DatabaseHost = "127.0.0.1",
  [int]$DatabasePort = 6001,
  [string]$DatabaseName = "mark",
  [string]$DatabaseUser = "mark",
  [string]$DatabasePassword = "markpassword"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$className = "数据库验收班（自动测试）"
$studentNo = "DBTEST-$stamp"

function Assert-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) {
    throw "验收失败：$Message"
  }
}

function Login-Mark {
  param(
    [string]$Username,
    [string]$Portal
  )
  $payload = @{
    username = $Username
    password = "123456"
    portal = $Portal
  } | ConvertTo-Json
  Invoke-RestMethod `
    -Uri "$BaseUrl/api/auth/session" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $payload `
    -SessionVariable session | Out-Null
  return $session
}

function Invoke-Mark {
  param(
    [string]$Method,
    [string]$Path,
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [object]$Body
  )
  $arguments = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    WebSession = $Session
  }
  if ($null -ne $Body) {
    $arguments.ContentType = "application/json; charset=utf-8"
    $arguments.Body = $Body | ConvertTo-Json -Depth 20
  }
  return Invoke-RestMethod @arguments
}

$teacherSession = Login-Mark -Username "teacher01" -Portal "TEACHER"
$classes = Invoke-Mark GET "/api/teacher/classes" $teacherSession
$class = $classes | Where-Object { $_.name -eq $className } | Select-Object -First 1
if (-not $class) {
  $class = Invoke-Mark POST "/api/teacher/classes" $teacherSession @{
    name = $className
    grade = "七年级"
    subject = "数学"
  }
}

$students = Invoke-Mark GET "/api/teacher/classes/$($class.id)/students?includeDisabled=true&pageSize=100" $teacherSession
$student = $students.items | Where-Object { $_.studentNo -eq $studentNo } | Select-Object -First 1
if (-not $student) {
  $student = Invoke-Mark POST "/api/teacher/classes/$($class.id)/students" $teacherSession @{
    studentNo = $studentNo
    name = "数据库验收学生"
  }
}

$homework = Invoke-Mark POST "/api/teacher/homeworks" $teacherSession @{
  classId = $class.id
  title = "数据库五步验收-$stamp"
  description = "自动验证新增题目、学生提交、自动判分、总分和错题本。"
  dueAt = (Get-Date).AddDays(7).ToString("o")
}

$question1 = Invoke-Mark POST "/api/teacher/homeworks/$($homework.id)/questions" $teacherSession @{
  seqNo = 1
  questionType = "SINGLE_CHOICE"
  stem = "计算 2 + 3 的结果。"
  options = @(
    @{ key = "A"; text = "4" },
    @{ key = "B"; text = "5" },
    @{ key = "C"; text = "6" },
    @{ key = "D"; text = "7" }
  )
  standardAnswer = "B"
  teacherAnalysis = "2 + 3 = 5，因此选择 B。"
  score = 2
  knowledgePointIds = @()
}
$question2 = Invoke-Mark POST "/api/teacher/homeworks/$($homework.id)/questions" $teacherSession @{
  seqNo = 2
  questionType = "TRUE_FALSE"
  stem = "所有偶数都能被 2 整除。"
  options = @(
    @{ key = "TRUE"; text = "正确" },
    @{ key = "FALSE"; text = "错误" }
  )
  standardAnswer = "TRUE"
  teacherAnalysis = "偶数定义就是能被 2 整除的整数。"
  score = 3
  knowledgePointIds = @()
}
$question3 = Invoke-Mark POST "/api/teacher/homeworks/$($homework.id)/questions" $teacherSession @{
  seqNo = 3
  questionType = "SINGLE_CHOICE"
  stem = "下列哪个数是负数？"
  options = @(
    @{ key = "A"; text = "-3" },
    @{ key = "B"; text = "0" },
    @{ key = "C"; text = "2" },
    @{ key = "D"; text = "5" }
  )
  standardAnswer = "A"
  teacherAnalysis = "-3 小于 0，是负数。"
  score = 5
  knowledgePointIds = @()
}

$latestHomework = Invoke-Mark GET "/api/teacher/homeworks/$($homework.id)" $teacherSession
$published = Invoke-Mark POST "/api/teacher/homeworks/$($homework.id)/publish" $teacherSession @{
  expectedVersion = $latestHomework.version
  remark = "数据库自动验收发布"
}
Assert-True ($published.status -eq "PUBLISHED") "作业没有成功发布。"

$studentSession = Login-Mark -Username $studentNo -Portal "STUDENT"
$studentHomeworks = Invoke-Mark GET "/api/student/homeworks" $studentSession
$visibleHomework = $studentHomeworks | Where-Object { $_.id -eq $homework.id } | Select-Object -First 1
Assert-True ($null -ne $visibleHomework) "新学生看不到刚发布的作业。"
Assert-True ($visibleHomework.questionCount -eq 3) "学生端题目数量不正确。"

$detail = Invoke-Mark GET "/api/student/homeworks/$($homework.id)" $studentSession
$detailJson = $detail | ConvertTo-Json -Depth 20
Assert-True ($detailJson -notmatch '"standardAnswer"') "学生题目视图泄露了标准答案。"
Assert-True ($detailJson -notmatch '"teacherAnalysis"') "学生题目视图泄露了教师解析。"
Assert-True ($detail.questions.Count -eq 3) "学生端没有读取到三道题。"

$submission = Invoke-Mark POST "/api/student/homeworks/$($homework.id)/submit" $studentSession @{
  answers = @{
    "$($question1.id)" = "B"
    "$($question2.id)" = "FALSE"
    "$($question3.id)" = "A"
  }
}
Assert-True ($submission.score -eq 7) "自动判分得分应为 7，实际为 $($submission.score)。"
Assert-True ($submission.maxScore -eq 10) "满分为 10，实际为 $($submission.maxScore)。"
Assert-True ($submission.scoreRate -eq 70) "正确率应为 70%，实际为 $($submission.scoreRate)。"
Assert-True ($submission.answers.Count -eq 3) "没有保存三道题的逐题结果。"
Assert-True (($submission.answers | Where-Object { -not $_.isCorrect }).Count -eq 1) "错误题数量应为 1。"

$score = Invoke-Mark GET "/api/student/score-summary" $studentSession
Assert-True ($score.totalScore -eq 7 -and $score.maxScore -eq 10) "学生总分快照不正确。"
Assert-True ($score.scoreRate -eq 70) "学生总分正确率不正确。"

$result = Invoke-Mark GET "/api/student/homeworks/$($homework.id)/result" $studentSession
Assert-True ($result.score -eq 7 -and $result.answers.Count -eq 3) "最近一次作答结果读取失败。"

$wrongBook = @(Invoke-Mark GET "/api/student/wrong-book?homeworkId=$($homework.id)" $studentSession)
Assert-True ($wrongBook.Count -eq 1) "错题本应只有 1 道错题，实际为 $($wrongBook.Count) 道。"
Assert-True ($wrongBook[0].questionId -eq $question2.id) "错题本记录了错误的题目。"
Assert-True ($wrongBook[0].selectedAnswer -eq "错误") "错题本中的学生答案不正确。"
Assert-True ($wrongBook[0].correctAnswer -eq "正确") "错题本中的标准答案不正确。"

$databaseVerification = $null
if (Test-Path -LiteralPath $MysqlClient) {
  $previousPassword = $env:MYSQL_PWD
  try {
    $env:MYSQL_PWD = $DatabasePassword
    $sql = @"
SELECT JSON_OBJECT(
  'submissionMatches', (SELECT COUNT(*) FROM student_homework_submission
    WHERE id = $($submission.submissionId) AND score = 7 AND maxScore = 10),
  'answerRows', (SELECT COUNT(*) FROM student_question_answer
    WHERE submissionId = $($submission.submissionId)),
  'wrongRows', (SELECT COUNT(*) FROM student_question_answer
    WHERE submissionId = $($submission.submissionId) AND isCorrect = 0),
  'studentExists', (SELECT COUNT(*) FROM app_user
    WHERE studentNo = '$studentNo' AND classId = $($class.id)),
  'homeworkExists', (SELECT COUNT(*) FROM course_homework
    WHERE id = $($homework.id) AND status = 'PUBLISHED'),
  'questionRows', (SELECT COUNT(*) FROM course_question
    WHERE homeworkId = $($homework.id) AND isDeleted = 0)
) AS verification;
"@
    $raw = & $MysqlClient `
      "--host=$DatabaseHost" `
      "--port=$DatabasePort" `
      "--user=$DatabaseUser" `
      "--protocol=tcp" `
      "--database=$DatabaseName" `
      "--batch" `
      "--skip-column-names" `
      "--execute=$sql"
    if ($LASTEXITCODE -ne 0) {
      throw "MySQL 直接校验执行失败。"
    }
    $databaseVerification = $raw | ConvertFrom-Json
    Assert-True ($databaseVerification.submissionMatches -eq 1) "数据库中缺少本次提交或分数快照。"
    Assert-True ($databaseVerification.answerRows -eq 3) "数据库中逐题答案没有完整保存。"
    Assert-True ($databaseVerification.wrongRows -eq 1) "数据库中错题快照数量不正确。"
    Assert-True ($databaseVerification.studentExists -eq 1) "数据库中缺少新增学生。"
    Assert-True ($databaseVerification.homeworkExists -eq 1) "数据库中缺少已发布作业。"
    Assert-True ($databaseVerification.questionRows -eq 3) "数据库中新增题目没有完整保存。"
  } finally {
    $env:MYSQL_PWD = $previousPassword
  }
}

[ordered]@{
  verifiedAt = (Get-Date).ToString("o")
  classId = $class.id
  studentId = $student.id
  studentNo = $studentNo
  homeworkId = $homework.id
  homeworkTitle = $published.title
  questionIds = @($question1.id, $question2.id, $question3.id)
  submissionId = $submission.submissionId
  score = $score
  wrongBookCount = $wrongBook.Count
  result = [ordered]@{
    score = $result.score
    maxScore = $result.maxScore
    scoreRate = $result.scoreRate
    answerCount = $result.answers.Count
  }
  studentViewProtected = $true
  databaseVerification = $databaseVerification
} | ConvertTo-Json -Depth 12
