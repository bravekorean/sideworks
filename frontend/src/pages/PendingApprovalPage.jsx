import { useState } from 'react'

const pendingApprovals = [
  {
    approvalId: 71,
    documentNumber: 'AP-2026-071',
    title: '개발팀 업무용 노트북 구매 요청',
    writerName: '박준호',
    documentType: '비품 구매',
    submittedAt: '2026-07-28 09:42',
    currentStep: 1,
    totalSteps: 2,
    status: 'PENDING',
  },
  {
    approvalId: 68,
    documentNumber: 'AP-2026-068',
    title: '2026년 하반기 교육비 지원 신청',
    writerName: '이서윤',
    documentType: '교육 신청',
    submittedAt: '2026-07-27 16:18',
    currentStep: 2,
    totalSteps: 3,
    status: 'PENDING',
  },
  {
    approvalId: 63,
    documentNumber: 'AP-2026-063',
    title: '7월 프로젝트 외근 교통비 정산',
    writerName: '최민석',
    documentType: '비용 정산',
    submittedAt: '2026-07-26 11:05',
    currentStep: 1,
    totalSteps: 1,
    status: 'PENDING',
  },
]

const approvalStatusLabels = {
  PENDING: '결재 대기',
}

const PAGE_SIZE = 2

function PendingApprovalPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [currentPage, setCurrentPage] = useState(1)

  const normalizedQuery = searchQuery.trim().toLowerCase()
  const filteredApprovals = pendingApprovals.filter((approval) => {
    const matchesSearch = [
      approval.documentNumber,
      approval.title,
      approval.writerName,
    ].some((value) => value.toLowerCase().includes(normalizedQuery))

    const matchesStatus =
      statusFilter === 'ALL' || approval.status === statusFilter

    return matchesSearch && matchesStatus
  })

  const totalPages = Math.max(
    1,
    Math.ceil(filteredApprovals.length / PAGE_SIZE),
  )
  const pageStartIndex = (currentPage - 1) * PAGE_SIZE
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
          <span className="section-kicker">APPROVAL</span>
          <h1>결재 대기함</h1>
          <p>현재 내가 처리해야 하는 결재 문서를 확인합니다.</p>
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
              <option value="PENDING">결재 대기</option>
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
                <th>상신일</th>
                <th>상태</th>
              </tr>
            </thead>

            <tbody>
              {visibleApprovals.length > 0 ? (
                visibleApprovals.map((approval) => (
                  <tr key={approval.approvalId}>
                    <td className="approval-document-number">
                      {approval.documentNumber}
                    </td>
                    <td>
                      <button className="approval-title-button" type="button">
                        {approval.title}
                      </button>
                    </td>
                    <td>{approval.writerName}</td>
                    <td>{approval.documentType}</td>
                    <td>
                      {approval.currentStep} / {approval.totalSteps}
                    </td>
                    <td>{approval.submittedAt}</td>
                    <td>
                      <span className="status-chip status-chip--pending">
                        {approvalStatusLabels[approval.status]}
                      </span>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="approval-empty-state" colSpan="7">
                    <strong>검색 결과가 없습니다.</strong>
                    <span>다른 검색어나 상태 조건을 확인해 주세요.</span>
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
              disabled={currentPage === 1}
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
                    currentPage === pageNumber ? 'page' : undefined
                  }
                  className={
                    currentPage === pageNumber ? 'pagination-button--active' : ''
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
              disabled={currentPage === totalPages}
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

export default PendingApprovalPage
