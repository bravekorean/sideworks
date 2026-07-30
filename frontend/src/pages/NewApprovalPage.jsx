import { useState } from 'react'

const documentTypes = [
  '품의서',
  '비품 구매',
  '비용 정산',
  '교육 신청',
  '근무 신청',
]

const availableApprovers = [
  { userId: 2, name: '김관리', department: '개발팀', position: '팀장' },
  { userId: 3, name: '이총괄', department: '경영지원팀', position: '본부장' },
  { userId: 4, name: '최민호', department: '재무팀', position: '팀장' },
]

const availableCcUsers = [
  { userId: 5, name: '정하늘', department: '개발팀' },
  { userId: 6, name: '오세진', department: 'QA팀' },
  { userId: 7, name: '이서윤', department: '인사팀' },
]

function NewApprovalPage() {
  const [documentType, setDocumentType] = useState(documentTypes[0])
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [approverIds, setApproverIds] = useState([2])
  const [ccUserIds, setCcUserIds] = useState([])
  const [feedback, setFeedback] = useState('')

  const toggleApprover = (userId) => {
    setApproverIds((currentIds) =>
      currentIds.includes(userId)
        ? currentIds.filter((id) => id !== userId)
        : [...currentIds, userId],
    )
  }

  const toggleCcUser = (userId) => {
    setCcUserIds((currentIds) =>
      currentIds.includes(userId)
        ? currentIds.filter((id) => id !== userId)
        : [...currentIds, userId],
    )
  }

  const handleSubmit = (event) => {
    event.preventDefault()

    if (!title.trim() || !content.trim() || approverIds.length === 0) {
      setFeedback('제목, 내용, 결재자를 모두 입력해 주세요.')
      return
    }

    setFeedback(
      '화면 입력 검증이 완료되었습니다. 실제 상신은 API 연결 후 처리됩니다.',
    )
  }

  const handleSaveDraft = () => {
    setFeedback(
      '현재는 목업 화면입니다. 임시저장 API 연결 후 서버에 저장됩니다.',
    )
  }

  return (
    <div className="approval-compose-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">NEW APPROVAL</span>
          <h1>새 결재 작성</h1>
          <p>결재 내용과 결재선을 지정하여 새로운 문서를 상신합니다.</p>
        </div>
        <span className="compose-draft-state">작성 중</span>
      </header>

      <form className="compose-layout" onSubmit={handleSubmit}>
        <div className="compose-main">
          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">01</span>
              <div>
                <h2>문서 정보</h2>
                <p>문서 종류와 제목을 입력해 주세요.</p>
              </div>
            </div>

            <div className="compose-form-grid">
              <label className="form-field">
                <span>문서 종류</span>
                <select
                  onChange={(event) => setDocumentType(event.target.value)}
                  value={documentType}
                >
                  {documentTypes.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </label>

              <label className="form-field form-field--wide">
                <span>제목</span>
                <input
                  maxLength={200}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder="결재 문서 제목을 입력하세요."
                  value={title}
                />
                <small>{title.length} / 200</small>
              </label>
            </div>
          </section>

          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">02</span>
              <div>
                <h2>결재 내용</h2>
                <p>검토자가 이해할 수 있도록 요청 배경과 내용을 작성해 주세요.</p>
              </div>
            </div>

            <label className="form-field">
              <span className="sr-only">결재 내용</span>
              <textarea
                onChange={(event) => setContent(event.target.value)}
                placeholder="결재 내용을 입력하세요."
                value={content}
              />
              <small>{content.length.toLocaleString()}자</small>
            </label>
          </section>
        </div>

        <aside className="compose-side">
          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">03</span>
              <div>
                <h2>결재선</h2>
                <p>승인 순서대로 결재자를 선택합니다.</p>
              </div>
            </div>

            <div className="selectable-user-list">
              {availableApprovers.map((approver) => {
                const selected = approverIds.includes(approver.userId)
                const approvalStep = approverIds.indexOf(approver.userId) + 1

                return (
                  <label
                    className={`selectable-user ${selected ? 'selectable-user--selected' : ''}`}
                    key={approver.userId}
                  >
                    <input
                      checked={selected}
                      onChange={() => toggleApprover(approver.userId)}
                      type="checkbox"
                    />
                    <span className="selectable-user__avatar">
                      {selected ? approvalStep : approver.name.slice(0, 1)}
                    </span>
                    <span className="selectable-user__copy">
                      <strong>{approver.name}</strong>
                      <small>
                        {approver.department} · {approver.position}
                      </small>
                    </span>
                  </label>
                )
              })}
            </div>
          </section>

          <section className="panel compose-panel">
            <div className="compose-section-heading">
              <span className="compose-section-number">04</span>
              <div>
                <h2>참조자</h2>
                <p>문서를 함께 확인할 구성원을 선택합니다.</p>
              </div>
            </div>

            <div className="cc-chip-list">
              {availableCcUsers.map((user) => (
                <label
                  className={`cc-select-chip ${ccUserIds.includes(user.userId) ? 'cc-select-chip--selected' : ''}`}
                  key={user.userId}
                >
                  <input
                    checked={ccUserIds.includes(user.userId)}
                    onChange={() => toggleCcUser(user.userId)}
                    type="checkbox"
                  />
                  <span>{user.name}</span>
                  <small>{user.department}</small>
                </label>
              ))}
            </div>
          </section>
        </aside>

        <footer className="compose-actions">
          <div aria-live="polite" className="compose-feedback">
            {feedback}
          </div>
          <button
            className="compose-button compose-button--secondary"
            onClick={handleSaveDraft}
            type="button"
          >
            임시저장
          </button>
          <button
            className="compose-button compose-button--primary"
            type="submit"
          >
            결재 상신
          </button>
        </footer>
      </form>
    </div>
  )
}

export default NewApprovalPage
