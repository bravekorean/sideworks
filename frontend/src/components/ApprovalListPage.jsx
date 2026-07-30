import { useMemo, useState } from 'react'
import { Link } from 'react-router'

const PAGE_SIZE = 5

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

function ApprovalListPage({
  approvals,
  dateLabel,
  description,
  emptyMessage,
  eyebrow = 'APPROVAL',
  statusOptions,
  title,
}) {
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [currentPage, setCurrentPage] = useState(1)

  const filteredApprovals = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase()

    return approvals.filter((approval) => {
      const matchesSearch = [
        approval.documentNumber,
        approval.title,
        approval.writerName,
        approval.documentType,
      ].some((value) => value.toLowerCase().includes(normalizedQuery))

      const matchesStatus =
        statusFilter === 'ALL' || approval.status === statusFilter

      return matchesSearch && matchesStatus
    })
  }, [approvals, searchQuery, statusFilter])

  const totalPages = Math.max(
    1,
    Math.ceil(filteredApprovals.length / PAGE_SIZE),
  )
  const safeCurrentPage = Math.min(currentPage, totalPages)
  const pageStartIndex = (safeCurrentPage - 1) * PAGE_SIZE
  const visibleApprovals = filteredApprovals.slice(
    pageStartIndex,
    pageStartIndex + PAGE_SIZE,
  )

  const handleSearchChange = (event) => {
    setSearchQuery(event.target.value)
    setCurrentPage(1)
  }

  const handleStatusChange = (event) => {
    setStatusFilter(event.target.value)
    setCurrentPage(1)
  }

  return (
    <div className="approval-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">{eyebrow}</span>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        <span className="approval-result-count">
          총 <strong>{filteredApprovals.length}</strong>건
        </span>
      </header>

      <section className="panel approval-table-panel">
        <div className="approval-toolbar">
          <label className="approval-search">
            <span aria-hidden="true">⌕</span>
            <input
              onChange={handleSearchChange}
              placeholder="문서번호, 제목, 작성자 검색"
              type="search"
              value={searchQuery}
            />
          </label>

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
        </div>

        <div className="approval-table-wrapper">
          <table className="approval-table">
            <thead>
              <tr>
                <th>문서번호</th>
                <th>제목</th>
                <th>작성자</th>
                <th>문서 종류</th>
                <th>결재 단계</th>
                <th>{dateLabel}</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {visibleApprovals.length > 0 ? (
                visibleApprovals.map((approval) => (
                  <tr key={`${approval.box}-${approval.approvalId}`}>
                    <td className="approval-document-number">
                      {approval.documentNumber}
                    </td>
                    <td>
                      {approval.detailAvailable ? (
                        <Link
                          className="approval-title-button"
                          to={`/approvals/${approval.approvalId}`}
                        >
                          {approval.title}
                        </Link>
                      ) : (
                        <span className="approval-title-text">
                          {approval.title}
                        </span>
                      )}
                    </td>
                    <td>{approval.writerName}</td>
                    <td>{approval.documentType}</td>
                    <td>
                      {approval.currentStep === null
                        ? '미상신'
                        : `${approval.currentStep} / ${approval.totalSteps}`}
                    </td>
                    <td>{approval.displayDate}</td>
                    <td>
                      <span
                        className={`status-chip status-chip--${statusClassNames[approval.status]}`}
                      >
                        {statusLabels[approval.status]}
                      </span>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="approval-empty-state" colSpan={7}>
                    <strong>{emptyMessage}</strong>
                    <span>검색어나 상태 조건을 변경해 보세요.</span>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="approval-pagination">
          <span>
            {filteredApprovals.length > 0
              ? `${pageStartIndex + 1}-${Math.min(
                  pageStartIndex + PAGE_SIZE,
                  filteredApprovals.length,
                )} / ${filteredApprovals.length}`
              : '0 / 0'}
          </span>

          <div className="pagination-buttons">
            <button
              disabled={safeCurrentPage === 1}
              onClick={() => setCurrentPage((page) => page - 1)}
              type="button"
            >
              이전
            </button>

            {Array.from({ length: totalPages }, (_, index) => {
              const pageNumber = index + 1

              return (
                <button
                  aria-current={
                    safeCurrentPage === pageNumber ? 'page' : undefined
                  }
                  className={
                    safeCurrentPage === pageNumber
                      ? 'pagination-button--active'
                      : ''
                  }
                  key={pageNumber}
                  onClick={() => setCurrentPage(pageNumber)}
                  type="button"
                >
                  {pageNumber}
                </button>
              )
            })}

            <button
              disabled={safeCurrentPage === totalPages}
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
