import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getApprovalBox } from '../api/approvalApi'

const PAGE_SIZE = 5

const emptyPage = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

const statusLabels = {
  DRAFT: '임시저장',
  IN_PROGRESS: '결재 진행',
  APPROVED: '승인 완료',
  REJECTED: '반려',
  CANCELED: '상신 취소',
  PENDING: '결재 대기',
}

const statusClassNames = {
  DRAFT: 'draft',
  IN_PROGRESS: 'progress',
  APPROVED: 'approved',
  REJECTED: 'rejected',
  CANCELED: 'canceled',
  PENDING: 'pending',
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 16)
}

function getDisplayDate(approval, box) {
  if (box === 'drafts') {
    return formatDateTime(approval.createdAt)
  }

  return formatDateTime(approval.submittedAt ?? approval.createdAt)
}

function getDisplayStatus(approval, box) {
  return box === 'pending' ? 'PENDING' : approval.approvalStatus
}

function ApprovalListPage({
  box,
  dateLabel,
  description,
  emptyMessage,
  eyebrow = 'APPROVAL',
  statusOptions,
  title,
}) {
  const [pageResponse, setPageResponse] = useState(emptyPage)
  const [currentPage, setCurrentPage] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [isListLoading, setIsListLoading] = useState(true)
  const [listLoadError, setListLoadError] = useState('')

  useEffect(() => {
    const debounceTimer = window.setTimeout(() => {
      setKeyword(searchQuery.trim())
      setCurrentPage(0)
    }, 300)

    return () => {
      window.clearTimeout(debounceTimer)
    }
  }, [searchQuery])

  useEffect(() => {
    let isActive = true

    const loadApprovals = async () => {
      try {
        setIsListLoading(true)
        setListLoadError('')

        const response = await getApprovalBox(box, {
          page: currentPage,
          size: PAGE_SIZE,
          keyword,
          status: statusFilter === 'ALL' ? '' : statusFilter,
        })

        if (isActive) {
          setPageResponse(response)
        }
      } catch (error) {
        if (isActive) {
          setPageResponse(emptyPage)
          setListLoadError(
            error.response?.data?.message ??
              '결재 문서 목록을 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) {
          setIsListLoading(false)
        }
      }
    }

    loadApprovals()

    return () => {
      isActive = false
    }
  }, [box, currentPage, keyword, statusFilter])

  const handleStatusChange = (event) => {
    setStatusFilter(event.target.value)
    setCurrentPage(0)
  }

  const rangeStart =
    pageResponse.totalElements === 0
      ? 0
      : pageResponse.page * pageResponse.size + 1
  const rangeEnd = Math.min(
    (pageResponse.page + 1) * pageResponse.size,
    pageResponse.totalElements,
  )

  return (
    <div className="approval-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">{eyebrow}</span>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        <span className="approval-result-count">
          총 <strong>{pageResponse.totalElements}</strong>건
        </span>
      </header>

      <section className="panel approval-table-panel">
        <div className="approval-toolbar">
          <label className="approval-search">
            <span aria-hidden="true">⌕</span>
            <input
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="제목, 작성자 검색"
              type="search"
              value={searchQuery}
            />
          </label>

          {statusOptions.length > 0 && (
            <label className="approval-status-filter">
              <span>상태</span>
              <select onChange={handleStatusChange} value={statusFilter}>
                <option value="ALL">전체</option>
                {statusOptions.map((status) => (
                  <option key={status} value={status}>
                    {statusLabels[status]}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>

        <div className="approval-table-wrapper">
          <table className="approval-table">
            <thead>
              <tr>
                <th>문서번호</th>
                <th>제목</th>
                <th>작성자</th>
                <th>결재 단계</th>
                <th>{dateLabel}</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {isListLoading ? (
                <tr>
                  <td className="approval-empty-state" colSpan={6}>
                    <strong>결재 문서를 불러오는 중입니다.</strong>
                  </td>
                </tr>
              ) : listLoadError ? (
                <tr>
                  <td className="approval-empty-state" colSpan={6}>
                    <strong>{listLoadError}</strong>
                    <span>잠시 후 다시 시도해 주세요.</span>
                  </td>
                </tr>
              ) : pageResponse.content.length > 0 ? (
                pageResponse.content.map((approval) => {
                  const displayStatus = getDisplayStatus(approval, box)

                  return (
                    <tr key={`${box}-${approval.approvalId}`}>
                      <td className="approval-document-number">
                        {displayStatus === 'DRAFT'
                          ? `DRAFT-${approval.approvalId}`
                          : `AP-${approval.approvalId}`}
                      </td>
                      <td>
                        <Link
                          className="approval-title-button"
                          state={{
                            from: `/approvals/${box}`,
                            fromLabel: title,
                          }}
                          to={
                            displayStatus === 'DRAFT'
                              ? `/approvals/${approval.approvalId}/edit`
                              : `/approvals/${approval.approvalId}`
                          }
                        >
                          {approval.title}
                        </Link>
                      </td>
                      <td>{approval.writerName}</td>
                      <td>
                        {approval.currentStep === null
                          ? '미상신'
                          : `${approval.currentStep}단계`}
                      </td>
                      <td>{getDisplayDate(approval, box)}</td>
                      <td>
                        <span
                          className={`status-chip status-chip--${statusClassNames[displayStatus]}`}
                        >
                          {statusLabels[displayStatus] ?? displayStatus}
                        </span>
                      </td>
                    </tr>
                  )
                })
              ) : (
                <tr>
                  <td className="approval-empty-state" colSpan={6}>
                    <strong>{emptyMessage}</strong>
                    {(keyword || statusFilter !== 'ALL') && (
                      <span>검색어나 상태 조건을 변경해 보세요.</span>
                    )}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="approval-pagination">
          <span>
            {rangeStart}-{rangeEnd} / {pageResponse.totalElements}
          </span>

          <div className="pagination-buttons">
            <button
              disabled={pageResponse.first || isListLoading}
              onClick={() => setCurrentPage((page) => page - 1)}
              type="button"
            >
              이전
            </button>

            {Array.from(
              { length: pageResponse.totalPages },
              (_, index) => index,
            ).map((pageNumber) => (
              <button
                aria-current={
                  currentPage === pageNumber ? 'page' : undefined
                }
                className={
                  currentPage === pageNumber
                    ? 'pagination-button--active'
                    : ''
                }
                key={pageNumber}
                onClick={() => setCurrentPage(pageNumber)}
                type="button"
              >
                {pageNumber + 1}
              </button>
            ))}

            <button
              disabled={pageResponse.last || isListLoading}
              onClick={() => setCurrentPage((page) => page + 1)}
              type="button"
            >
              다음
            </button>
          </div>
        </footer>
      </section>
    </div>
  )
}

export default ApprovalListPage
