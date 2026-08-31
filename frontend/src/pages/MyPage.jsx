import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { logout } from '../api/authApi'
import {
  changeMyPassword,
  getMyProfile,
  updateMyProfile,
  withdrawMyAccount,
} from '../api/userApi'

const roleLabels = {
  SUPER_ADMIN: '최고 관리자',
  ADMIN: '관리자',
  USER: '일반 사용자',
}

const statusLabels = {
  ACTIVE: '재직 중',
  INACTIVE: '비활성',
  DELETED: '탈퇴',
}

const jobFamilyLabels = {
  TECHNICAL: '기술직렬',
  CORPORATE: '경영직렬',
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 16)
}

function MyPage() {
  const navigate = useNavigate()

  const [myProfile, setMyProfile] = useState(null)
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [profileSaving, setProfileSaving] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [profileFeedback, setProfileFeedback] = useState('')
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false)
  const [passwordFeedback, setPasswordFeedback] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [withdrawalDialogOpen, setWithdrawalDialogOpen] = useState(false)
  const [withdrawalFeedback, setWithdrawalFeedback] = useState('')
  const [withdrawing, setWithdrawing] = useState(false)

  useEffect(() => {
    let active = true

    getMyProfile()
      .then((profile) => {
        if (!active) {
          return
        }

        setMyProfile(profile)
        setEmail(profile.userEmail ?? '')
        setPhone(profile.userPhone ?? '')
      })
      .catch((error) => {
        if (!active) {
          return
        }

        setLoadError(
          error.response?.data?.message ??
            '내 정보를 불러오는 중 오류가 발생했습니다.',
        )
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

const handleProfileSubmit = async (event) => {
  event.preventDefault()

  setProfileSaving(true)
  setProfileFeedback('')

  try {
    await updateMyProfile(
      email.trim() || null,
      phone.trim() || null,
    )

    const updatedProfile = await getMyProfile()

    setMyProfile(updatedProfile)
    setEmail(updatedProfile.userEmail ?? '')
    setPhone(updatedProfile.userPhone ?? '')
    setProfileFeedback('내 정보가 수정되었습니다.')
  } catch (error) {
    const message =
      error.response?.data?.message ??
      '내 정보를 수정하는 중 오류가 발생했습니다.'

    setProfileFeedback(message)
  } finally {
    setProfileSaving(false)
  }
}



const handlePasswordSubmit = async (event) => {
  event.preventDefault()

  const form = event.currentTarget
  const currentPassword = form.currentPassword.value
  const newPassword = form.newPassword.value
  const passwordConfirm = form.passwordConfirm.value

  if (!currentPassword || !newPassword || !passwordConfirm) {
    setPasswordFeedback('모든 비밀번호 항목을 입력해 주세요.')
    return
  }

  if (newPassword !== passwordConfirm) {
    setPasswordFeedback('새 비밀번호 확인이 일치하지 않습니다.')
    return
  }

  if (newPassword.length < 8 || newPassword.length > 72) {
    setPasswordFeedback('새 비밀번호는 8자 이상 72자 이하여야 합니다.')
    return
  }

  setPasswordSaving(true)
  setPasswordFeedback('')

  try {
    await changeMyPassword(currentPassword, newPassword)

    form.reset()
    setPasswordFeedback('비밀번호가 변경되었습니다.')
  } catch (error) {
    const message =
      error.response?.data?.message ??
      '비밀번호를 변경하는 중 오류가 발생했습니다.'

    setPasswordFeedback(message)
  } finally {
    setPasswordSaving(false)
  }
}

  const handleWithdrawalSubmit = async (event) => {
    event.preventDefault()

    const form = event.currentTarget
    const password = form.password.value

    if (!password) {
      setWithdrawalFeedback('현재 비밀번호를 입력해 주세요.')
      return
    }

    setWithdrawing(true)
    setWithdrawalFeedback('')

    try {
      await withdrawMyAccount(password)
      await logout().catch(() => undefined)

      sessionStorage.removeItem('accessToken')
      navigate('/login', { replace: true })
    } catch (error) {
      const message =
        error.response?.data?.message ??
        '회원 탈퇴를 처리하는 중 오류가 발생했습니다.'

      setWithdrawalFeedback(message)
    } finally {
      setWithdrawing(false)
    }
  }

  if (loading) {
    return (
      <div className="mypage-page">
        <section className="panel detail-not-found">
          <span>LOADING</span>
          <h1>내 정보를 불러오고 있습니다.</h1>
        </section>
      </div>
    )
  }

  if (loadError || !myProfile) {
    return (
      <div className="mypage-page">
        <section className="panel detail-not-found">
          <span>ERROR</span>
          <h1>내 정보를 불러오지 못했습니다.</h1>
          <p>{loadError}</p>
        </section>
      </div>
    )
  }

  return (
    <div className="mypage-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">MY ACCOUNT</span>
          <h1>마이페이지</h1>
          <p>내 계정과 연락처, 보안 정보를 확인하고 관리합니다.</p>
        </div>
        <span className="mypage-status">
          <i /> {statusLabels[myProfile.status] ?? myProfile.status}
        </span>
      </header>

      <section className="panel mypage-profile-hero">
        <div className="mypage-avatar">{myProfile.userName.slice(0, 1)}</div>
        <div className="mypage-profile-copy">
          <span>{myProfile.employeeNo}</span>
          <h2>{myProfile.userName}</h2>
          <p>
            {myProfile.departmentName ?? '미배정'} ·{' '}
            {myProfile.positionName ?? '미배정'}
          </p>
        </div>
        <div className="mypage-role-card">
          <span>계정 권한</span>
          <strong>{roleLabels[myProfile.userRole] ?? myProfile.userRole}</strong>
          <small>{myProfile.userRole}</small>
        </div>
      </section>

      <div className="mypage-grid">
        <form className="panel mypage-info-panel" onSubmit={handleProfileSubmit}>
          <header className="mypage-panel-header">
            <div>
              <span className="section-kicker">PROFILE</span>
              <h2>내 정보</h2>
              <p>이메일과 휴대폰번호만 직접 변경할 수 있습니다.</p>
            </div>
            <span>마지막 수정 {formatDateTime(myProfile.updatedAt)}</span>
          </header>

          <div className="mypage-form-grid">
            <label className="form-field">
              <span>로그인 ID</span>
              <input disabled value={myProfile.loginId} />
            </label>
            <label className="form-field">
              <span>이름</span>
              <input disabled value={myProfile.userName} />
            </label>
            <label className="form-field">
              <span>사번</span>
              <input disabled value={myProfile.employeeNo} />
            </label>
            <label className="form-field">
              <span>직렬</span>
              <input
                disabled
                value={jobFamilyLabels[myProfile.jobFamily] ?? '기존 계정'}
              />
            </label>
            <label className="form-field">
              <span>입사일</span>
              <input disabled value={myProfile.hireDate ?? '-'} />
            </label>
            <label className="form-field">
              <span>부서 / 직급</span>
              <input
                disabled
                value={`${myProfile.departmentName ?? '미배정'} / ${myProfile.positionName ?? '미배정'}`}
              />
            </label>
            <label className="form-field">
              <span>이메일</span>
              <input
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                value={email}
              />
            </label>
            <label className="form-field">
              <span>휴대폰번호</span>
              <input
                onChange={(event) => setPhone(event.target.value)}
                value={phone}
              />
            </label>
          </div>

          <footer className="mypage-form-actions">
            <p aria-live="polite">{profileFeedback}</p>
            <button
              className="admin-primary-button"
              disabled={profileSaving}
              type="submit"
            >
              {profileSaving ? '저장 중...' : '변경사항 저장'}
            </button>
          </footer>
        </form>

        <aside className="mypage-side">
          <section className="panel mypage-security-panel">
            <header className="mypage-panel-header">
              <div>
                <span className="section-kicker">SECURITY</span>
                <h2>보안 설정</h2>
              </div>
            </header>

            <div className="security-setting-row">
              <span className="security-icon">•••</span>
              <div>
                <strong>비밀번호</strong>
                <small>현재 비밀번호 확인 후 변경할 수 있습니다.</small>
              </div>
              <button onClick={() => {setPasswordFeedback(''), setPasswordDialogOpen(true)}} type="button">
                변경
              </button>
            </div>
          </section>

          <section className="panel mypage-account-panel">
            <span className="section-kicker">ACCOUNT</span>
            <h2>계정 관리</h2>
            <p>
              탈퇴 시 비밀번호를 다시 확인하고 계정 상태를 삭제로 변경합니다.
            </p>
            <button
              className="danger-outline-button"
              onClick={() => {
                setWithdrawalFeedback('')
                setWithdrawalDialogOpen(true)
              }}
              type="button"
            >
              회원 탈퇴
            </button>
          </section>

          <section className="panel mypage-system-panel">
            <span>사용자 ID</span>
            <strong>{myProfile.userId}</strong>
            <span>계정 생성일</span>
            <strong>{formatDateTime(myProfile.createdAt)}</strong>
          </section>
        </aside>
      </div>

      {passwordDialogOpen && (
        <div className="decision-dialog-backdrop" role="presentation">
          <form
            aria-labelledby="password-dialog-title"
            className="decision-dialog password-change-dialog"
            onSubmit={handlePasswordSubmit}
          >
            <header className="create-user-dialog__header">
              <div>
                <span className="section-kicker">CHANGE PASSWORD</span>
                <h2 id="password-dialog-title">비밀번호 변경</h2>
                <p>현재 비밀번호와 사용할 새 비밀번호를 입력하세요.</p>
              </div>
              <button
                aria-label="닫기"
                onClick={() => setPasswordDialogOpen(false)}
                type="button"
              >
                ×
              </button>
            </header>

            <div className="management-form">
              <label className="form-field">
                <span>현재 비밀번호</span>
                <input name="currentPassword" type="password" />
              </label>
              <label className="form-field">
                <span>새 비밀번호</span>
                <input name="newPassword" type="password" />
              </label>
              <label className="form-field">
                <span>새 비밀번호 확인</span>
                <input name="passwordConfirm" type="password" />
              </label>
            </div>

            <p aria-live="polite" className="create-user-feedback">
              {passwordFeedback}
            </p>

            <footer className="decision-dialog__actions">
              <button
                onClick={() => setPasswordDialogOpen(false)}
                type="button"
              >
                취소
              </button>
              <button className="dialog-confirm" disabled={passwordSaving} type="submit">
                {passwordSaving ? '변경 중...' : '비밀번호 변경'}
              </button>
            </footer>
          </form>
        </div>
      )}

      {withdrawalDialogOpen && (
        <div className="decision-dialog-backdrop" role="presentation">
          <form
            aria-labelledby="withdrawal-dialog-title"
            className="decision-dialog password-change-dialog"
            onSubmit={handleWithdrawalSubmit}
          >
            <header className="create-user-dialog__header">
              <div>
                <span className="section-kicker">DELETE ACCOUNT</span>
                <h2 id="withdrawal-dialog-title">회원 탈퇴</h2>
                <p>본인 확인을 위해 현재 비밀번호를 입력하세요.</p>
              </div>
              <button
                aria-label="닫기"
                onClick={() => setWithdrawalDialogOpen(false)}
                type="button"
              >
                ×
              </button>
            </header>

            <div className="management-form">
              <label className="form-field">
                <span>현재 비밀번호</span>
                <input
                  autoComplete="current-password"
                  name="password"
                  type="password"
                />
              </label>
            </div>

            <p aria-live="polite" className="create-user-feedback">
              {withdrawalFeedback}
            </p>

            <footer className="decision-dialog__actions">
              <button
                disabled={withdrawing}
                onClick={() => setWithdrawalDialogOpen(false)}
                type="button"
              >
                취소
              </button>
              <button
                className="dialog-confirm--reject"
                disabled={withdrawing}
                type="submit"
              >
                {withdrawing ? '처리 중...' : '탈퇴하기'}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  )
}

export default MyPage
