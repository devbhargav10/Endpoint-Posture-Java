# AWS Terraform foundation

This is an infrastructure-only starting point for the production target described in `Doc/DECISIONS.md` D2 and `Doc/ROADMAP.md`: a Linux Spring API, PostgreSQL system of record, and centrally hosted Windows runners. The older `Doc/AWS_DEPLOYMENT_GUIDE.md` describes a Windows-hosted lab deployment; it is not the target represented here.

## Included foundation

- A two-AZ VPC with public edge, private application/runner, and isolated database subnets.
- Security group boundaries for a future TLS edge, API/worker traffic, and PostgreSQL.
- Optional NAT egress. NAT is off in the dev example and carries material hourly/data charges; production should generally use one per AZ if private workloads require internet egress.
- Encrypted, private PostgreSQL 16 RDS with automated backups, storage autoscaling, deletion protection, optional Multi-AZ, and an RDS-managed master credential in Secrets Manager.
- Separate example inputs and Terraform state keys for dev, staging, and production.

The API should use a task/instance role restricted to the exact Secrets Manager secret ARNs it needs and its CloudWatch log stream. Windows runners should use a separate EC2 instance profile limited to Systems Manager and approved logging; worker API credentials remain individually enrolled with the backend and are not Terraform inputs. Deployment automation should assume a scoped role through GitHub OIDC, never long-lived IAM user keys. No workload roles are instantiated until the compute and secret naming contracts are selected.

Send application and runner logs to CloudWatch with explicit retention and secret-safe logging. Monitor API availability/errors/latency, RDS CPU/storage/connections, and worker/job backlog using existing health and metrics endpoints. This foundation does not create log groups, alarms, dashboards, or a monitoring stack; those depend on the selected hosting runtime and retention/cost requirements.

This does not deploy application compute, an ALB, a TLS certificate, VPN/Direct Connect, Windows runners, or endpoint connectivity. The backend container image is not yet built in this repository. Windows workers are centrally hosted runner services, not an agent service installed on every endpoint. They need HTTPS connectivity to the API and network routes/credentials for their managed endpoint sites. Site routes must be deliberately added after CIDR and connectivity decisions.

The edge and worker security groups currently define the ingress boundaries, but workload egress remains permissive to keep this network-only foundation usable before endpoint-site, ISE, SSM, and package-registry destinations are known. Restrict egress to documented service/site CIDRs and required VPC endpoints before deploying workloads; this is a deployment gate, not a production-ready firewall policy.

No secrets belong in `.tfvars`, source control, Terraform outputs, or state. RDS manages only its master credential. The application database user, `DB_PASSWORD`, `JWT_SECRET`, `SEED_ADMIN_PASSWORD`, worker enrollment/key lifecycle, and ISE credentials still require a separate secret-provisioning design. Terraform state can contain sensitive infrastructure metadata and must be stored in a restricted, versioned, encrypted S3 bucket. The bucket and IAM access are a bootstrap prerequisite, not created by this root module. Backend examples use native S3 lockfiles; use separate keys and preferably separate AWS accounts for each environment.

## Layout

```text
terraform/
  modules/network/       # VPC, subnets, routes, security groups
  modules/postgres/      # RDS PostgreSQL, subnet group and restricted SG
  environments/          # Reviewed example variables and partial backend configs
  *.tf                   # Environment root composition
```

## Local checks

Use Terraform CLI 1.10 or newer and AWS provider 6.62.x. Formatting and validation do not create infrastructure or require AWS credentials:

```powershell
terraform fmt -check -recursive terraform
terraform -chdir=terraform init -backend=false
terraform -chdir=terraform validate
```

The validation workflow runs the same format check and initializes with `-backend=false`; it does not configure AWS credentials or apply changes. A provider lock file should be generated and reviewed with `terraform init` in the project’s approved environment, then committed to pin provider checksums.

For a deployment, first bootstrap the state bucket and restrict its access. Copy an environment `.backend.hcl.example` and `.tfvars.example` to local filenames (ignored by Git), replace placeholders, and confirm account/region/network choices. Initialize with the environment backend config and plan for review. No apply is authorized by this foundation task.

## Before first development deployment

Decide the AWS account and region, confirm CIDR non-overlap with endpoint/ISE networks, verify PostgreSQL 16 minor version and instance availability in that region, and determine what private workloads need outbound access. Choose backend/frontend hosting and packaging (the API is not currently containerized), TLS/domain ingress, worker placement, and site-to-site VPN or Direct Connect routing. Define the least-privilege workload IAM roles and secret access; use GitHub OIDC for CI rather than long-lived AWS keys. Set a budget, backup retention, recovery objectives, log retention, and production HA policy. Also decide who owns Terraform state bootstrap and database application-user creation/rotation.

The current CIDRs, region, database classes, and engine minor versions in examples are placeholders for review, not deployment-approved values.
