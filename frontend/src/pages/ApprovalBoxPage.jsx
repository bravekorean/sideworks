import ApprovalListPage from '../components/ApprovalListPage'

const approvalBoxConfigs = {
  drafts: {
    title: '임시저장함',
    description: '작성 중인 결재 문서를 이어서 작성할 수 있습니다.',
    dateLabel: '저장일',
    emptyMessage: '임시저장한 문서가 없습니다.',
    statusOptions: [],
  },
  sent: {
    title: '내가 작성한 문서',
    description: '내가 상신한 결재 문서의 진행 상태를 확인합니다.',
    dateLabel: '상신일',
    emptyMessage: '상신한 결재 문서가 없습니다.',
    statusOptions: [
      'IN_PROGRESS',
      'APPROVED',
      'REJECTED',
      'CANCELED',
    ],
  },
  pending: {
    title: '결재 대기함',
    description: '현재 내가 처리해야 하는 결재 문서를 확인합니다.',
    dateLabel: '상신일',
    emptyMessage: '처리할 결재 문서가 없습니다.',
    statusOptions: [],
  },
  processed: {
    title: '결재 처리함',
    description: '내가 처리한 결재 문서의 현재 진행 상태를 확인합니다.',
    dateLabel: '상신일',
    emptyMessage: '처리한 결재 문서가 없습니다.',
    statusOptions: [
      'IN_PROGRESS',
      'APPROVED',
      'REJECTED',
      'CANCELED',
    ],
  },
  cc: {
    title: '참조 문서함',
    description: '업무 참조자로 지정된 결재 문서와 진행 상태를 확인합니다.',
    dateLabel: '상신일',
    emptyMessage: '참조 중인 결재 문서가 없습니다.',
    statusOptions: [
      'IN_PROGRESS',
      'APPROVED',
      'REJECTED',
      'CANCELED',
    ],
  },
}

function ApprovalBoxPage({ box }) {
  const config = approvalBoxConfigs[box]

  return <ApprovalListPage box={box} key={box} {...config} />
}

export default ApprovalBoxPage
