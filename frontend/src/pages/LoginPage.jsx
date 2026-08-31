import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { login } from '../api/authApi'

function LoginPage() {
  const navigate = useNavigate()

  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [rememberLoginId, setRememberLoginId] = useState(false)
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [feedback, setFeedback] = useState('')

  const handleSubmit = async (event) => {
    event.preventDefault()

    if (!loginId.trim() || !password) {
      setFeedback('로그인 ID와 비밀번호를 모두 입력해 주세요.')
      return
    }

    try {
      const loginResponse = await login(loginId, password)

      sessionStorage.setItem('accessToken', loginResponse.accessToken)

      setFeedback(`${loginResponse.userName}님, 로그인되었습니다.`)
      navigate('/dashboard')
    } catch (error) {
      const message =
        error.response?.data?.message ??
        '로그인 요청 중 오류가 발생했습니다.'

      setFeedback(message)
    }
  }

  return (
    <main className="login-page">
      <section className="login-visual" aria-label="SideWorks 소개">
        <div className="login-visual__glow" />
        <Link className="login-brand" to="/login">
          <span>S</span>
          <strong>SideWorks</strong>
        </Link>

        <div className="login-visual__copy">
          <span className="section-kicker">WORK BETTER, TOGETHER</span>
          <h1>
            결재와 조직 업무를
            <br />
            하나의 흐름으로.
          </h1>
          <p>
            SideWorks는 사용자, 조직, 전자결재 업무를 연결하는
            <br />
            그룹웨어 워크스페이스입니다.
          </p>
        </div>

        <div className="login-feature-list">
          <div>
            <span>01</span>
            <strong>전자결재</strong>
            <small>상신부터 최종 승인까지</small>
          </div>
          <div>
            <span>02</span>
            <strong>조직 관리</strong>
            <small>부서와 구성원을 한눈에</small>
          </div>
          <div>
            <span>03</span>
            <strong>업무 현황</strong>
            <small>처리할 업무를 빠르게</small>
          </div>
        </div>
      </section>

      <section className="login-form-section">
        <form className="login-form" onSubmit={handleSubmit}>
          <header>
            <span className="section-kicker">WELCOME BACK</span>
            <h2>SideWorks 로그인</h2>
            <p>업무를 계속하려면 계정으로 로그인하세요.</p>
          </header>

          <div className="login-fields">
            <label className="form-field login-field">
              <span>로그인 ID</span>
              <input
                autoComplete="username"
                onChange={(event) => setLoginId(event.target.value)}
                placeholder="로그인 ID를 입력하세요."
                value={loginId}
              />
            </label>

            <label className="form-field login-field">
              <span>비밀번호</span>
              <span className="password-input-wrap">
                <input
                  autoComplete="current-password"
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="비밀번호를 입력하세요."
                  type={passwordVisible ? 'text' : 'password'}
                  value={password}
                />
                <button
                  aria-label={
                    passwordVisible ? '비밀번호 숨기기' : '비밀번호 표시'
                  }
                  onClick={() => setPasswordVisible((visible) => !visible)}
                  type="button"
                >
                  {passwordVisible ? '숨김' : '보기'}
                </button>
              </span>
            </label>
          </div>

          <label className="remember-login">
            <input
              checked={rememberLoginId}
              onChange={(event) => setRememberLoginId(event.target.checked)}
              type="checkbox"
            />
            <span>로그인 ID 기억하기</span>
          </label>

          <p aria-live="polite" className="login-feedback">
            {feedback}
          </p>

          <button className="login-submit-button" type="submit">
            로그인
          </button>

          <Link className="login-preview-link" to="/dashboard">
            대시보드 미리보기 →
          </Link>

          <footer>
            <span>SideWorks V1</span>
            <span>학습 및 포트폴리오 프로젝트</span>
          </footer>
        </form>
      </section>
    </main>
  )
}

export default LoginPage
