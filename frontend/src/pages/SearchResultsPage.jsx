import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { searchApprovals } from '../api/approvalApi'

const PAGE_SIZE = 20

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
}

const statusClassNames = {
  DRAFT: 'draft',
  IN_PROGRESS: 'progress',
  APPROVED: 'approved',
  REJECTED: 'rejected',
  CANCELED: 'canceled',
}

function formatDateTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const keyword = searchParams.get('keyword')?.trim() ?? ''
  const requestedPage = Number(searchParams.get('page') ?? 0)
  const currentPage = Number.isInteger(requestedPage) && requestedPage >= 0
    ? requestedPage
    : 0
  const [pageResponse, setPageResponse] = useState(emptyPage)
  const [isLoading, setIsLoading] = useState(false)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let isActive = true

    if (!keyword) {
      return undefined
    }

    const loadSearchResults = async () => {
      try {
        setIsLoading(true)
        setLoadError('')
        const response = await searchApprovals(keyword, {
          page: currentPage,
          size: PAGE_SIZE,
        })

        if (isActive) setPageResponse(response)
      } catch (error) {
        if (isActive) {
          setPageResponse(emptyPage)
          setLoadError(
            error.response?.data?.message ??
              '통합 검색 결과를 불러오지 못했습니다.',
          )
        }
      } finally {
        if (isActive) setIsLoading(false)
      }
    }

    loadSearchResults()
    return () => {
      isActive = false
    }
  }, [currentPage, keyword])

  const moveToPage = (page) => {
    setSearchParams({ keyword, page: String(page) })
  }

  const displayedPage = keyword ? pageResponse : emptyPage
  const displayedLoading = Boolean(keyword) && isLoading
  const displayedError = keyword ? loadError : ''

  const rangeStart =
    displayedPage.totalElements === 0
      ? 0
      : displayedPage.page * displayedPage.size + 1
  const rangeEnd = Math.min(
    (displayedPage.page + 1) * displayedPage.size,
    displayedPage.totalElements,
  )

  return (
    <div className="approval-page">
      <header className="page-header">
        <div>
          <span className="section-kicker">GLOBAL SEARCH</span>
          <h1>통합 검색</h1>
          <p>결재 제목 또는 작성자·결재자·참조자로 문서를 검색합니다.</p>
        </div>
        <span className="approval-result-count">
          총 <strong>{displayedPage.totalElements}</strong>건
        </span>
      </header>

      <section className="panel approval-table-panel">
        <div className="approval-toolbar">
          <p className="search-keyword-summary">
            {keyword ? <><strong>“{keyword}”</strong> 검색 결과</> : '상단 검색창에 검색어를 입력해 주세요.'}
          </p>
        </div>

        <div className="approval-table-wrapper">
          <table className="approval-table">
            <thead><tr><th>문서번호</th><th>제목</th><th>작성자</th><th>결재 단계</th><th>작성일</th><th>상태</th></tr></thead>
            <tbody>
              {displayedLoading ? <tr><td className="approval-empty-state" colSpan={6}><strong>검색 중입니다.</strong></td></tr>
                : displayedError ? <tr><td className="approval-empty-state" colSpan={6}><strong>{displayedError}</strong><span>잠시 후 다시 시도해 주세요.</span></td></tr>
                  : displayedPage.content.length > 0 ? displayedPage.content.map((approval) => <tr key={approval.approvalId}>
                    <td className="approval-document-number">{approval.approvalStatus === 'DRAFT' ? `DRAFT-${approval.approvalId}` : `AP-${approval.approvalId}`}</td>
                    <td><Link className="approval-title-button" state={{ from: `/search?keyword=${encodeURIComponent(keyword)}`, fromLabel: '통합 검색' }} to={approval.approvalStatus === 'DRAFT' ? `/approvals/${approval.approvalId}/edit` : `/approvals/${approval.approvalId}`}>{approval.title}</Link></td>
                    <td>{approval.writerName}</td>
                    <td>{approval.currentStep === null ? '미상신' : `${approval.currentStep}단계`}</td>
                    <td>{formatDateTime(approval.submittedAt ?? approval.createdAt)}</td>
                    <td><span className={`status-chip status-chip--${statusClassNames[approval.approvalStatus]}`}>{statusLabels[approval.approvalStatus] ?? approval.approvalStatus}</span></td>
                  </tr>)
                    : <tr><td className="approval-empty-state" colSpan={6}><strong>{keyword ? '검색 결과가 없습니다.' : '검색어를 입력해 주세요.'}</strong>{keyword && <span>제목이나 참여 사용자 정보를 확인해 보세요.</span>}</td></tr>}
            </tbody>
          </table>
        </div>

        <footer className="approval-pagination">
          <span>{rangeStart}-{rangeEnd} / {displayedPage.totalElements}</span>
          <div className="pagination-buttons">
            <button disabled={displayedPage.first || displayedLoading} onClick={() => moveToPage(currentPage - 1)} type="button">이전</button>
            {Array.from({ length: displayedPage.totalPages }, (_, index) => index).map((pageNumber) => <button aria-current={currentPage === pageNumber ? 'page' : undefined} className={currentPage === pageNumber ? 'pagination-button--active' : ''} key={pageNumber} onClick={() => moveToPage(pageNumber)} type="button">{pageNumber + 1}</button>)}
            <button disabled={displayedPage.last || displayedLoading} onClick={() => moveToPage(currentPage + 1)} type="button">다음</button>
          </div>
        </footer>
      </section>
    </div>
  )
}

export default SearchResultsPage
