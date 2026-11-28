// 대기함에서만 서버가 채운다. 대결로 받은 문서와, 위임 중이지만 충돌로 내게 남은 문서를 구분한다.
function DelegationBadge({ approval }) {
  if (approval.delegationState === 'DELEGATED') {
    return <small className="delegation-note">{approval.originalApproverName} 대결</small>
  }
  if (approval.delegationState === 'DELEGATION_BLOCKED') {
    return <small className="delegation-note delegation-note--blocked">대결 불가</small>
  }
  return null
}

export default DelegationBadge
