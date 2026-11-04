@echo off
cd /d "%~dp0"

echo Target repo: https://github.com/woaiyrb/dazuoye.git
echo If a GitHub sign-in window appears, complete it there.
echo.

REM The push has to be authorized by an identity with the 'workflow' scope,
REM because this repo ships CI files under .github\workflows\. Using the
REM account-qualified URL is what made it work when the saved PAT-only
REM credential was rejected.
git push https://woaiyrb@github.com/woaiyrb/dazuoye.git main

if errorlevel 1 (
  echo.
  echo [FAILED] Push did not succeed.
  echo - Sign in to GitHub in the popup window and try again.
  echo - Keeping  .git\config  and this script free of tokens on purpose.
) else (
  echo.
  echo [OK] Up to date: https://github.com/woaiyrb/dazuoye
)

pause
