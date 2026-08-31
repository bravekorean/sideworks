import { useEffect, useMemo, useState } from 'react'
import {
  createPosition,
  deletePosition,
  getAdminPositions,
  updatePosition,
} from '../api/adminApi'

function PositionManagementPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [positions, setPositions] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [selectedPosition, setSelectedPosition] = useState(null)
  const [dialogMode, setDialogMode] = useState(null)
  const [feedback, setFeedback] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const loadPositions = async () => {
    try {
      setIsLoading(true)
      const pageResponse = await getAdminPositions()
      setPositions(pageResponse.content)
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '직급 목록을 불러오지 못했습니다.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    let isActive = true

    const loadInitialPositions = async () => {
      try {
        const pageResponse = await getAdminPositions()

        if (isActive) {
          setPositions(pageResponse.content)
        }
      } catch (error) {
        if (isActive) {
          setFeedback(
            error.response?.data?.message ??
              '직급 목록을 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) {
          setIsLoading(false)
        }
      }
    }

    loadInitialPositions()

    return () => {
      isActive = false
    }
  }, [])

  const filteredPositions = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase()

    return positions.filter((position) =>
      position.positionName.toLowerCase().includes(normalizedQuery),
    )
  }, [positions, searchQuery])

  const openCreateDialog = () => {
    setSelectedPosition(null)
    setFeedback('')
    setDialogMode('create')
  }

  const openEditDialog = (position) => {
    setSelectedPosition(position)
    setFeedback('')
    setDialogMode('edit')
  }

  const handleDialogSubmit = async (event) => {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    const positionName = formData.get('positionName').trim()
    const positionOrder = Number(formData.get('positionOrder'))

    try {
      setIsSubmitting(true)
      setFeedback('')

      if (dialogMode === 'create') {
        await createPosition(positionName, positionOrder)
      } else {
        await updatePosition(
          selectedPosition.positionId,
          positionName,
          positionOrder,
        )
      }

      setDialogMode(null)
      await loadPositions()
    } catch (error) {
      setFeedback(
        error.response?.data?.message ?? '직급 정보를 저장하지 못했습니다.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDelete = async (position) => {
    if (!window.confirm(`${position.positionName} 직급을 삭제할까요?`)) {
      return
    }

    try {
      setFeedback('')
      await deletePosition(position.positionId)
      await loadPositions()
    } catch (error) {
      setFeedback(
        error.response?.data?.message ??
          '사용 중인 직급은 삭제할 수 없습니다.',
      )
    }
  }

  return (
    <div className="admin-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">ADMIN · POSITION</span>
          <h1>직급 관리</h1>
          <p>조직에서 사용하는 직급과 표시 순서를 관리합니다.</p>
        </div>
        <button
          className="admin-primary-button"
          onClick={openCreateDialog}
          type="button"
        >
          <span aria-hidden="true">＋</span>
          직급 생성
        </button>
      </header>

      <section className="position-guide-panel">
        <div className="position-guide-icon" aria-hidden="true">
          ↕
        </div>
        <div>
          <strong>positionOrder는 직급의 표시 순서를 나타냅니다.</strong>
          <p>
            숫자가 작을수록 먼저 표시됩니다. 현재 DB의 UNIQUE 제약조건에 따라
            같은 순서는 사용할 수 없습니다.
          </p>
        </div>
      </section>

      <section className="panel position-panel">
        <div className="admin-toolbar">
          <label className="approval-search">
            <span aria-hidden="true">⌕</span>
            <input
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="직급명 검색"
              type="search"
              value={searchQuery}
            />
          </label>
          <span className="position-count">
            총 <strong>{filteredPositions.length}</strong>개 직급
          </span>
        </div>

        <div className="position-list-heading">
          <span>순서</span>
          <span>직급명</span>
          <span>소속 인원</span>
          <span>순서 조정</span>
          <span>관리</span>
        </div>

        {feedback && !dialogMode && (
          <p className="create-user-feedback">{feedback}</p>
        )}
        <div className="position-list">
          {isLoading && <p className="compose-feedback">불러오는 중입니다.</p>}
          {filteredPositions.map((position, index) => (
            <article className="position-row" key={position.positionId}>
              <span className="position-order">{position.positionOrder}</span>
              <div className="position-name">
                <span>{position.positionName.slice(0, 1)}</span>
                <div>
                  <strong>{position.positionName}</strong>
                  <small>POSITION ID · {position.positionId}</small>
                </div>
              </div>
              <span className="position-member-count">
                -
              </span>
              <div className="position-order-actions">
                <button
                  aria-label={`${position.positionName} 위로 이동`}
                  disabled={index === 0}
                  type="button"
                >
                  ↑
                </button>
                <button
                  aria-label={`${position.positionName} 아래로 이동`}
                  disabled={index === filteredPositions.length - 1}
                  type="button"
                >
                  ↓
                </button>
              </div>
              <div className="position-management-actions">
                <button
                  onClick={() => openEditDialog(position)}
                  type="button"
                >
                  수정
                </button>
                <button
                  className="danger-text-button"
                  onClick={() => handleDelete(position)}
                  type="button"
                >
                  삭제
                </button>
              </div>
            </article>
          ))}
        </div>

        <footer className="admin-table-footer">
          <span>직급이 배정된 사용자가 있으면 삭제가 제한됩니다.</span>
          <span>순서 변경 API는 현재 백엔드 구조를 확인한 후 연결합니다.</span>
        </footer>
      </section>

      {dialogMode && (
        <div className="decision-dialog-backdrop" role="presentation">
          <form
            aria-labelledby="position-dialog-title"
            className="decision-dialog management-dialog"
            onSubmit={handleDialogSubmit}
          >
            <header className="create-user-dialog__header">
              <div>
                <span className="section-kicker">
                  {dialogMode === 'create' ? 'NEW POSITION' : 'EDIT POSITION'}
                </span>
                <h2 id="position-dialog-title">
                  {dialogMode === 'create' ? '직급 생성' : '직급 정보 수정'}
                </h2>
                <p>직급명과 중복되지 않는 표시 순서를 입력합니다.</p>
              </div>
              <button
                aria-label="닫기"
                onClick={() => setDialogMode(null)}
                type="button"
              >
                ×
              </button>
            </header>

            <div className="management-form">
              <label className="form-field">
                <span>직급명</span>
                <input
                  defaultValue={selectedPosition?.positionName ?? ''}
                  name="positionName"
                  placeholder="직급명을 입력하세요."
                  required
                />
              </label>
              <label className="form-field">
                <span>표시 순서</span>
                <input
                  defaultValue={selectedPosition?.positionOrder ?? ''}
                  min="1"
                  name="positionOrder"
                  placeholder="1"
                  required
                  type="number"
                />
              </label>
            </div>

            <p aria-live="polite" className="create-user-feedback">
              {feedback}
            </p>

            <footer className="decision-dialog__actions">
              <button onClick={() => setDialogMode(null)} type="button">
                취소
              </button>
              <button
                className="dialog-confirm"
                disabled={isSubmitting}
                type="submit"
              >
                {isSubmitting
                  ? '저장 중...'
                  : dialogMode === 'create'
                    ? '생성하기'
                    : '저장하기'}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  )
}

export default PositionManagementPage
