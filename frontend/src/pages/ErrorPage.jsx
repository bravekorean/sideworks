import { Link } from 'react-router'

function ErrorPage({ code, description, title }) {
  return (
    <div className="system-error-page">
      <div className="system-error-visual" aria-hidden="true">
        <span>{code}</span>
        <i />
      </div>
      <span className="section-kicker">SIDEWORKS SYSTEM</span>
      <h1>{title}</h1>
      <p>{description}</p>
      <div className="system-error-actions">
        <Link className="admin-primary-button" to="/dashboard">
          대시보드로 이동
        </Link>
        <Link to="/login">로그인 화면</Link>
      </div>
    </div>
  )
}

export default ErrorPage
